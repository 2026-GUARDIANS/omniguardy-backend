package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingAgentRepository;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class JdbcStreamingAgentRepository implements StreamingAgentRepository {
    private final JdbcTemplate jdbc;
    private final Clock visionStreamingClock;
    private static final String SOURCE = """
            FROM vision_final_results f
            JOIN vision_sessions s ON s.session_id = f.session_id
            JOIN vision_polling_jobs v ON v.session_id = s.session_id
            LEFT JOIN keypad_camera_triggers k ON k.trigger_id = s.trigger_id
            WHERE f.eligible_for_agent = TRUE AND f.outcome = 'VALID' AND f.analysis_state = 'COMPLETED'
              AND f.result_status = 'success' AND f.fallback_required = FALSE
              AND s.status = 'COMPLETED' AND v.processing_state = 'SUCCEEDED' AND v.trigger_type = s.trigger_type
              AND ((s.trigger_type = 'KEYPAD' AND f.analysis_completeness = 'COMPLETE_KEYPAD'
                    AND s.event_id IS NULL AND v.security_event_id IS NULL AND s.trigger_id = v.trigger_id)
                OR (s.trigger_type = 'AUDIO' AND f.analysis_completeness = 'COMPLETE_AUDIO'
                    AND s.event_id = v.security_event_id AND s.trigger_id IS NULL))
            """;

    @Override public List<String> discover(int limit) {
        return jdbc.queryForList("SELECT f.session_id " + SOURCE + """
                 AND NOT EXISTS (SELECT 1 FROM streaming_agent_jobs a WHERE a.session_id = f.session_id)
                 ORDER BY f.received_at, f.session_id LIMIT ?
                """, String.class, limit);
    }

    @Override public void enqueue(String id) {
        var now = sql(now());
        try {
            jdbc.update("""
                    INSERT INTO streaming_agent_jobs (session_id, processing_state, next_attempt_at, created_at)
                    SELECT f.session_id, 'PENDING', ?, ?
                    """ + SOURCE + " AND f.session_id = ? AND NOT EXISTS (SELECT 1 FROM streaming_agent_jobs a WHERE a.session_id = f.session_id)",
                    now, now, id);
        } catch (DuplicateKeyException duplicate) { /* Concurrent discovery already enqueued this session. */ }
        catch (TransientDataAccessException retryNextScan) { /* A deadlocked discovery is safely retried on the next scan. */ }
    }

    @Override public Optional<StreamingAgentSource> source(String id) {
        return jdbc.query("SELECT s.session_id, s.trigger_type, s.event_id, v.trigger_id, k.triggered_at, f.raw_result_json "
                + SOURCE + " AND f.session_id = ?", (rs, row) -> {
            var triggered = rs.getObject("triggered_at", LocalDateTime.class);
            return new StreamingAgentSource(new VisionBinding(rs.getString("session_id"),
                    VisionTriggerType.valueOf(rs.getString("trigger_type")), rs.getString("trigger_id"), rs.getString("event_id")),
                    triggered == null ? null : triggered.atOffset(ZoneOffset.UTC), rs.getString("raw_result_json"));
        }, id).stream().findFirst();
    }

    @Override public Optional<StreamingAgentJob> find(String id) {
        return jdbc.query("SELECT * FROM streaming_agent_jobs WHERE session_id = ?", this::map, id).stream().findFirst();
    }
    @Override public List<String> due(int limit) {
        var now = sql(now());
        return jdbc.queryForList("""
                SELECT session_id FROM streaming_agent_jobs
                WHERE (processing_state = 'PENDING' AND next_attempt_at <= ?)
                   OR (processing_state = 'PREPARING' AND lease_until <= ?)
                ORDER BY next_attempt_at, session_id LIMIT ?
                """, String.class, now, now, limit);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<StreamingAgentJob> claim(String id, Duration lease) {
        var now = now();
        int count = jdbc.update("""
                UPDATE streaming_agent_jobs SET processing_state = 'PREPARING', attempts = attempts + 1,
                    lease_token = ?, lease_until = ?
                WHERE session_id = ? AND ((processing_state = 'PENDING' AND next_attempt_at <= ?)
                    OR (processing_state = 'PREPARING' AND lease_until <= ?))
                """, UUID.randomUUID().toString(), sql(now.plus(lease)), id, sql(now), sql(now));
        return count == 1 ? find(id) : Optional.empty();
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockPreparation(StreamingAgentJob job) {
        return jdbc.query("SELECT * FROM streaming_agent_jobs WHERE session_id = ? FOR UPDATE", this::map, job.sessionId())
                .stream().anyMatch(current -> current.state() == StreamingAgentJob.State.PREPARING
                        && Objects.equals(current.leaseToken(), job.leaseToken()) && current.leaseUntil().isAfter(now()));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void prepared(StreamingAgentJob job, String eventId) {
        jdbc.update("UPDATE streaming_agent_jobs SET event_id = ? WHERE session_id = ? AND lease_token = ? AND processing_state = 'PREPARING'",
                eventId, job.sessionId(), job.leaseToken());
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean startCalling(StreamingAgentJob job, Duration lease) {
        var now = now();
        return jdbc.update("""
                UPDATE streaming_agent_jobs SET processing_state = 'CALLING', agent_attempts = agent_attempts + 1,
                    lease_until = ?, response_state = 'OUTCOME_UNKNOWN'
                WHERE session_id = ? AND processing_state = 'PREPARING' AND event_id IS NOT NULL
                    AND lease_token = ? AND lease_until > ?
                """ + " AND EXISTS (SELECT 1 " + SOURCE + " AND f.session_id = streaming_agent_jobs.session_id)",
                sql(now.plus(lease)), job.sessionId(), job.leaseToken(), sql(now)) == 1;
    }

    @Override public boolean isCalling(StreamingAgentJob job) {
        return find(job.sessionId()).filter(current -> current.state() == StreamingAgentJob.State.CALLING
                && Objects.equals(current.leaseToken(), job.leaseToken()) && current.leaseUntil().isAfter(now())).isPresent();
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(StreamingAgentJob job, StreamingAgentJob.State state, String reason, String responseState) {
        var now = now();
        // A late successful caller may resolve its own UNCERTAIN attempt, but never another token.
        String condition = state == StreamingAgentJob.State.SUCCEEDED
                ? " AND processing_state IN ('CALLING', 'UNCERTAIN')"
                : " AND processing_state IN ('PREPARING', 'CALLING') AND lease_until > ?";
        var args = new ArrayList<Object>(List.of(state.name(), reason, responseState, state == StreamingAgentJob.State.BLOCKED,
                sql(now), job.sessionId(), job.leaseToken()));
        if (state != StreamingAgentJob.State.SUCCEEDED) args.add(sql(now));
        jdbc.update("""
                UPDATE streaming_agent_jobs SET processing_state = ?, reason = ?, response_state = ?, fallback_required = ?, finished_at = ?,
                    lease_until = NULL WHERE session_id = ? AND lease_token = ?
                """ + condition, args.toArray());
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(StreamingAgentJob job, Duration delay, int maxAttempts, String reason) {
        var now = now();
        jdbc.update("""
                UPDATE streaming_agent_jobs SET processing_state = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at = ?, lease_until = NULL, lease_token = NULL, reason = ?, last_error = ?,
                    finished_at = CASE WHEN attempts >= ? THEN ? ELSE NULL END, response_state = 'NOT_STARTED'
                WHERE session_id = ? AND lease_token = ? AND processing_state IN ('PREPARING', 'CALLING') AND lease_until > ?
                """, maxAttempts, sql(now.plus(delay)), reason, reason, maxAttempts, sql(now), job.sessionId(), job.leaseToken(), sql(now));
    }

    @Override public List<StreamingAgentJob> unresolved(int limit) {
        return jdbc.query("""
                SELECT a.* FROM streaming_agent_jobs a
                WHERE (a.processing_state = 'CALLING' AND a.lease_until <= ?)
                  OR (a.processing_state = 'UNCERTAIN' AND EXISTS
                    (SELECT 1 FROM security_events e WHERE e.event_id = a.event_id AND e.status = 'COMPLETED'
                     AND e.risk_level IS NOT NULL AND e.risk_score IS NOT NULL AND e.agent_reason IS NOT NULL))
                ORDER BY a.created_at, a.session_id LIMIT ?
                """, this::map, sql(now()), limit);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(StreamingAgentJob job, boolean completed) {
        jdbc.update("""
                UPDATE streaming_agent_jobs SET processing_state = ?, reason = ?, response_state = 'UNKNOWN_NOT_REPLAYED',
                    lease_until = NULL, finished_at = ? WHERE session_id = ? AND lease_token = ?
                    AND ((processing_state = 'CALLING' AND lease_until <= ?) OR processing_state = 'UNCERTAIN')
                """, completed ? "SUCCEEDED" : "UNCERTAIN", completed ? "RECOVERED_ASSESSMENT" : "INTERRUPTED_CALL",
                sql(now()), job.sessionId(), job.leaseToken(), sql(now()));
    }

    private StreamingAgentJob map(ResultSet rs, int row) throws SQLException {
        return new StreamingAgentJob(rs.getString("session_id"), rs.getString("event_id"),
                StreamingAgentJob.State.valueOf(rs.getString("processing_state")), rs.getInt("attempts"), rs.getInt("agent_attempts"),
                instant(rs, "next_attempt_at"), rs.getString("lease_token"), instant(rs, "lease_until"),
                rs.getString("reason"), rs.getString("response_state"), rs.getBoolean("fallback_required"));
    }
    private Instant now() { return visionStreamingClock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static LocalDateTime sql(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        var value = rs.getObject(field, LocalDateTime.class); return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
