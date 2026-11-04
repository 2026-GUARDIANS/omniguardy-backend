package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.visionsession.application.port.out.VisionPollingRepository;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.omniguardy.backend.global.performance.PerformanceLog;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;

@Repository
@RequiredArgsConstructor
public class JdbcVisionPollingRepository implements VisionPollingRepository {
    private final JdbcTemplate jdbc;
    private final Clock visionStreamingClock;

    @Override
    public Optional<VisionPollingJob> find(String id) {
        return jdbc.query("SELECT * FROM vision_polling_jobs WHERE session_id = ?", this::map, id).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VisionPollingJob enqueue(VisionBinding binding, Duration maxWait) {
        var session = jdbc.query("SELECT * FROM vision_sessions WHERE session_id = ? FOR UPDATE", (rs, row) ->
                new VisionBinding(rs.getString("session_id"), VisionTriggerType.valueOf(rs.getString("trigger_type")),
                        rs.getString("trigger_id"), rs.getString("event_id")), binding.sessionId())
                .stream().findFirst().orElseThrow(() -> new BusinessException(VISION_SESSION_NOT_FOUND));
        var existing = find(binding.sessionId());
        if (existing.isPresent()) return existing.get();
        if (session.triggerType() != binding.triggerType() || !Objects.equals(session.securityEventId(), binding.securityEventId())
                || (session.triggerType() == VisionTriggerType.KEYPAD && !Objects.equals(session.triggerId(), binding.triggerId())))
            throw new BusinessException(VISION_SESSION_CONFLICT);
        String state = jdbc.queryForObject("SELECT status FROM vision_sessions WHERE session_id = ?", String.class, binding.sessionId());
        if (!"REGISTERED".equals(state)) throw new BusinessException(INVALID_VISION_SESSION_STATUS);
        var now = now();
        jdbc.update("""
                INSERT INTO vision_polling_jobs (session_id, trigger_type, trigger_id, security_event_id, processing_state,
                    requested_at, deadline_at, next_poll_at, attempts)
                VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, 0)
                """, binding.sessionId(), binding.triggerType().name(), binding.triggerId(), binding.securityEventId(),
                sql(now), sql(now.plus(maxWait)), sql(now));
        return find(binding.sessionId()).orElseThrow();
    }

    @Override
    public List<String> findDue(int limit) {
        var now = sql(now());
        return jdbc.queryForList("""
                SELECT session_id FROM vision_polling_jobs
                WHERE (processing_state = 'PENDING' AND next_poll_at <= ?)
                   OR (processing_state = 'RUNNING' AND lease_until <= ?)
                ORDER BY next_poll_at, session_id LIMIT ?
                """, String.class, now, now, limit);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<VisionPollingJob> claim(String id, Duration leaseDuration) {
        var now = now();
        String token = UUID.randomUUID().toString();
        int changed = jdbc.update("""
                UPDATE vision_polling_jobs SET processing_state = 'RUNNING', lease_token = ?, lease_until = ?, attempts = attempts + 1
                WHERE session_id = ? AND ((processing_state = 'PENDING' AND next_poll_at <= ?)
                    OR (processing_state = 'RUNNING' AND lease_until <= ?))
                """, token, sql(now.plus(leaseDuration)), id, sql(now), sql(now));
        return changed == 1 ? find(id) : Optional.empty();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reschedule(VisionPollingJob claim, Duration delay, String reason, String error, String statusJson) {
        var now = now();
        if (!now.isBefore(claim.deadlineAt())) {
            finishWithinTransaction(claim, null, VisionFinalOutcome.FAILED, "TIMEOUT", "TIMEOUT", statusJson, now);
            return;
        }
        var next = now.plus(delay);
        if (next.isAfter(claim.deadlineAt())) next = claim.deadlineAt();
        jdbc.update("""
                UPDATE vision_polling_jobs SET processing_state = 'PENDING', next_poll_at = ?, lease_token = NULL,
                    lease_until = NULL, reason = ?, last_error = ?, last_status_json = COALESCE(?, last_status_json)
                WHERE session_id = ? AND processing_state = 'RUNNING' AND lease_token = ? AND lease_until > ?
                """, sql(next), reason, error, statusJson, claim.binding().sessionId(), claim.leaseToken(), sql(now));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(VisionPollingJob claim, StreamingVisionFinalResult result, VisionFinalOutcome outcome,
                       String reason, String error, String statusJson) {
        finishWithinTransaction(claim, result, outcome, reason, error, statusJson, now());
    }

    private void finishWithinTransaction(VisionPollingJob claim, StreamingVisionFinalResult result, VisionFinalOutcome outcome,
                                         String reason, String error, String statusJson, Instant now) {
        try (var scope = PerformanceLog.scope(claim.binding().securityEventId(), claim.binding().triggerId(),
                claim.binding().sessionId(), claim.binding().triggerType().name(), claim.attempts())) {
            PerformanceLog.transaction("VISION_FINAL_STORE", () -> persistFinal(claim, result, outcome, reason, error, statusJson, now));
        }
    }

    private void persistFinal(VisionPollingJob claim, StreamingVisionFinalResult result, VisionFinalOutcome outcome,
                              String reason, String error, String statusJson, Instant now) {
        var current = jdbc.query("SELECT * FROM vision_polling_jobs WHERE session_id = ? FOR UPDATE", this::map,
                claim.binding().sessionId()).stream().findFirst();
        if (current.isEmpty() || current.get().state() != VisionProcessingState.RUNNING
                || !Objects.equals(current.get().leaseToken(), claim.leaseToken())
                || !current.get().leaseUntil().isAfter(now)) {
            PerformanceLog.result("STALE_IGNORED"); return; // Stale/crashed worker is fenced out.
        }
        if (!now.isBefore(current.get().deadlineAt())) {
            result = null; outcome = VisionFinalOutcome.FAILED; reason = "TIMEOUT"; error = "TIMEOUT";
        }
        boolean valid = outcome == VisionFinalOutcome.VALID;
        if (result != null) {
            jdbc.update("""
                    INSERT INTO vision_final_results (session_id, analysis_state, analysis_completeness, result_status,
                        outcome, eligible_for_agent, fallback_required, tracked_person_count, vision_events_json,
                        related_early_event_ids_json, raw_result_json, emitted_at, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, claim.binding().sessionId(), result.analysisState().name(), result.analysisCompleteness().name(),
                    result.status(), outcome.name(), valid, !valid, result.trackedPersonCount(), result.visionEventsJson(),
                    result.relatedEarlyEventInstanceIdsJson(), result.rawJson(), result.emittedAt(), sql(now));
        }
        int updated = jdbc.update("UPDATE vision_sessions SET status = ? WHERE session_id = ? AND status = 'REGISTERED'",
                valid ? "COMPLETED" : "FAILED", claim.binding().sessionId());
        if (updated != 1) throw new BusinessException(INVALID_VISION_SESSION_STATUS);
        jdbc.update("""
                UPDATE vision_polling_jobs SET processing_state = ?, lease_token = NULL, lease_until = NULL,
                    reason = ?, last_error = ?, last_status_json = COALESCE(?, last_status_json), finished_at = ?
                WHERE session_id = ?
                """, valid ? "SUCCEEDED" : "FALLBACK_REQUIRED", reason, error, statusJson, sql(now), claim.binding().sessionId());
        PerformanceLog.result(reason);
    }

    private VisionPollingJob map(ResultSet rs, int row) throws SQLException {
        return new VisionPollingJob(new VisionBinding(rs.getString("session_id"),
                VisionTriggerType.valueOf(rs.getString("trigger_type")), rs.getString("trigger_id"), rs.getString("security_event_id")),
                VisionProcessingState.valueOf(rs.getString("processing_state")), instant(rs, "requested_at"),
                instant(rs, "deadline_at"), instant(rs, "next_poll_at"), instant(rs, "lease_until"), rs.getString("lease_token"),
                rs.getInt("attempts"), rs.getString("reason"), rs.getString("last_error"), rs.getString("last_status_json"));
    }

    private Instant now() { return visionStreamingClock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static LocalDateTime sql(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        var value = rs.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
