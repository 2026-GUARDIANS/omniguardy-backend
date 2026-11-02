package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository @RequiredArgsConstructor
public class JdbcVisionFallbackRepository implements VisionFallbackRepository {
    private final JdbcTemplate jdbc;
    private final Clock visionStreamingClock;
    private final StreamingVideoRepository videos;
    private final ObjectMapper mapper;
    // Agent failures/retries/uncertainty are never interpreted as Vision failure.
    private static final String ELIGIBLE = """
            FROM vision_sessions s
            LEFT JOIN vision_polling_jobs p ON p.session_id=s.session_id
            LEFT JOIN vision_final_results f ON f.session_id=s.session_id
            LEFT JOIN streaming_agent_jobs a ON a.session_id=s.session_id
            WHERE ((p.processing_state='FALLBACK_REQUIRED' OR f.outcome IN ('PARTIAL','FAILED','REJECTED'))
                AND (a.session_id IS NULL OR (a.processing_state='BLOCKED' AND a.fallback_required=TRUE AND a.agent_attempts=0 AND a.event_id IS NULL))
              OR (a.processing_state='BLOCKED' AND a.fallback_required=TRUE AND a.agent_attempts=0 AND a.event_id IS NULL))
            """;
    private boolean eligible(String id) { return !jdbc.queryForList("SELECT s.session_id " + ELIGIBLE + " AND s.session_id=?", String.class, id).isEmpty(); }
    private void lockSession(String id) { jdbc.queryForList("SELECT session_id FROM vision_sessions WHERE session_id=? FOR UPDATE", String.class, id); }

    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public void discover(VisionFallbackPolicy policy) {
        var ids = jdbc.queryForList("SELECT s.session_id " + ELIGIBLE + " AND NOT EXISTS (SELECT 1 FROM vision_fallback_jobs b WHERE b.session_id=s.session_id) ORDER BY s.registered_at,s.session_id LIMIT ?", String.class, policy.batchSize());
        for (String id : ids) {
            lockSession(id);
            if (!eligible(id) || find(id).isPresent()) continue;
            var now = now();
            jdbc.update("INSERT INTO vision_fallback_jobs (session_id,processing_state,reason,created_at,video_deadline_at,next_attempt_at) VALUES (?,'PENDING','STREAMING_FALLBACK_REQUIRED',?,?,?)",
                    id, sql(now), sql(now.plus(policy.videoWait())), sql(now));
        }
    }
    @Override public List<String> due(int limit) {
        return jdbc.queryForList("""
                SELECT session_id FROM vision_fallback_jobs WHERE
                  (processing_state IN ('PENDING','WAITING_VIDEO') AND next_attempt_at<=?)
                  OR (processing_state IN ('RUNNING','PREPARED') AND lease_until<=?)
                ORDER BY next_attempt_at,session_id LIMIT ?
                """, String.class, sql(now()), sql(now()), limit);
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Optional<VisionFallbackJob> claim(String id, VisionFallbackPolicy policy) {
        lockSession(id);
        var rows = jdbc.query("SELECT * FROM vision_fallback_jobs WHERE session_id=? FOR UPDATE", this::map, id);
        if (rows.isEmpty()) return Optional.empty();
        var job = rows.getFirst(); var now = now();
        boolean pending = Set.of(VisionFallbackJob.State.PENDING, VisionFallbackJob.State.WAITING_VIDEO).contains(job.state());
        boolean expired = Set.of(VisionFallbackJob.State.RUNNING, VisionFallbackJob.State.PREPARED).contains(job.state()) && !job.leaseUntil().isAfter(now);
        if ((!pending && !expired) || (pending && jdbc.queryForObject("SELECT next_attempt_at>? FROM vision_fallback_jobs WHERE session_id=?", Boolean.class, sql(now), id))) return Optional.empty();
        if (!eligible(id)) {
            jdbc.update("UPDATE vision_fallback_jobs SET processing_state='SKIPPED',reason='STREAMING_OWNS_SESSION',lease_until=NULL,finished_at=? WHERE session_id=?", sql(now), id);
            return Optional.empty();
        }
        jdbc.update("UPDATE vision_fallback_jobs SET attempts=attempts+CASE WHEN processing_state='WAITING_VIDEO' THEN 0 ELSE 1 END,processing_state='RUNNING',lease_token=?,lease_until=? WHERE session_id=?",
                UUID.randomUUID().toString(), sql(now.plus(policy.visionLease())), id);
        return find(id);
    }
    @Override public Optional<VisionFallbackJob> find(String id) { return jdbc.query("SELECT * FROM vision_fallback_jobs WHERE session_id=?", this::map, id).stream().findFirst(); }
    @Override public boolean owns(VisionFallbackJob claim) {
        return find(claim.sessionId()).filter(job -> Set.of(VisionFallbackJob.State.RUNNING, VisionFallbackJob.State.PREPARED).contains(job.state())
                && Objects.equals(job.leaseToken(),claim.leaseToken()) && job.leaseUntil().isAfter(now())).isPresent();
    }
    @Override public VisionFallbackSource source(VisionFallbackJob claim) {
        if (!owns(claim)) throw new IllegalStateException("STALE_FALLBACK_CLAIM");
        var context = jdbc.queryForObject("""
                SELECT s.trigger_type,s.event_id,s.trigger_id,p.trigger_id AS poll_trigger,p.security_event_id AS poll_event,
                       e.trigger_id AS audio_trigger,e.trigger_type AS audio_type,e.triggered_at AS audio_at,k.triggered_at AS keypad_at
                FROM vision_sessions s LEFT JOIN vision_polling_jobs p ON p.session_id=s.session_id
                LEFT JOIN security_events e ON e.event_id=s.event_id
                LEFT JOIN keypad_camera_triggers k ON k.trigger_id=s.trigger_id WHERE s.session_id=?
                """, (rs,n) -> {
            var type = TriggerType.valueOf(rs.getString("trigger_type"));
            String event = rs.getString("event_id"), trigger = type==TriggerType.AUDIO ? rs.getString("audio_trigger") : rs.getString("trigger_id");
            var at = rs.getObject(type==TriggerType.AUDIO ? "audio_at" : "keypad_at",LocalDateTime.class);
            if (trigger==null || at==null || !Objects.equals(trigger,rs.getString("poll_trigger"))
                    || !Objects.equals(event,rs.getString("poll_event")) || (type==TriggerType.AUDIO && !"AUDIO".equals(rs.getString("audio_type"))))
                throw new IllegalArgumentException("FALLBACK_BINDING_MISMATCH");
            return new VideoTriggerContext(event,trigger,type,event,at.atOffset(ZoneOffset.UTC).toString());
        },claim.sessionId());
        var video = videos.find(claim.sessionId()).filter(v -> v.state()==StreamingSessionVideo.State.STORED);
        video.ifPresent(v -> {
            if (!v.binding().triggerType().name().equals(context.triggerType().name()) || !Objects.equals(v.binding().triggerId(),context.triggerId())
                    || !Objects.equals(v.binding().securityEventId(),context.securityEventId())) throw new IllegalArgumentException("VIDEO_BINDING_MISMATCH");
        });
        return new VisionFallbackSource(context,video);
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean beginVision(VisionFallbackJob claim, StreamingSessionVideo video, int maxAttempts) {
        return jdbc.update("""
                UPDATE vision_fallback_jobs SET vision_attempts=vision_attempts+1,video_sha256=?,video_storage_type=?,video_namespace=?,video_path=?
                WHERE session_id=? AND processing_state='RUNNING' AND lease_token=? AND lease_until>? AND vision_attempts<? AND vision_json IS NULL
                """,video.sha256(),video.target().storageType(),video.target().namespace(),video.target().path(),claim.sessionId(),claim.leaseToken(),sql(now()),maxAttempts)==1;
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean analyzed(VisionFallbackJob claim, VisionAnalysis analysis) {
        return jdbc.update("UPDATE vision_fallback_jobs SET vision_json=?,reason='MP4_ANALYZED' WHERE session_id=? AND processing_state='RUNNING' AND lease_token=? AND lease_until>?",
                mapper.writeValueAsString(analysis),claim.sessionId(),claim.leaseToken(),sql(now()))==1;
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void waitForVideo(VisionFallbackJob claim, VisionFallbackPolicy policy) {
        if (!claim.videoDeadline().isAfter(now())) { finish(claim,VisionFallbackJob.State.FAILED,"VIDEO_WAIT_TIMEOUT"); return; }
        reschedule(claim,policy,"WAITING_VIDEO","WAITING_VIDEO");
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void retry(VisionFallbackJob claim, VisionFallbackPolicy policy, String reason, boolean permanent) {
        var current = find(claim.sessionId()).orElseThrow();
        if (permanent || current.visionAttempts()>=policy.maxVisionAttempts() || current.attempts()>=policy.maxPreparationAttempts())
            finish(claim,VisionFallbackJob.State.FAILED,reason);
        else reschedule(claim,policy,"PENDING",reason);
    }
    private void reschedule(VisionFallbackJob claim, VisionFallbackPolicy policy,String state,String reason) {
        jdbc.update("UPDATE vision_fallback_jobs SET processing_state=?,reason=?,last_error=?,next_attempt_at=?,lease_until=NULL WHERE session_id=? AND processing_state IN ('RUNNING','PREPARED') AND lease_token=? AND lease_until>?",
                state,reason,reason,sql(now().plus(policy.retryDelay())),claim.sessionId(),claim.leaseToken(),sql(now()));
    }
    @Override @Transactional(propagation=Propagation.REQUIRED)
    public void finish(VisionFallbackJob claim, VisionFallbackJob.State state,String reason) {
        int updated=jdbc.update("UPDATE vision_fallback_jobs SET processing_state=?,reason=?,last_error=?,lease_until=NULL,finished_at=?,response_state=? WHERE session_id=? AND lease_token=? AND processing_state IN ('RUNNING','PREPARED','CALLING') AND lease_until>?",
                state.name(),reason,state==VisionFallbackJob.State.FAILED ? reason : null,sql(now()),
                state==VisionFallbackJob.State.UNCERTAIN ? "UNKNOWN_NOT_REPLAYED" : state==VisionFallbackJob.State.SUCCEEDED && claim.agentAttempts()>0 ? "BEST_EFFORT_ATTEMPTED" : "NOT_STARTED",
                claim.sessionId(),claim.leaseToken(),sql(now()));
        if(updated==1 && claim.eventId()!=null && (state==VisionFallbackJob.State.UNCERTAIN || state==VisionFallbackJob.State.SUCCEEDED))
            jdbc.update("UPDATE security_agent_executions SET execution_state=?,updated_at=? WHERE event_id=? AND execution_token=? AND execution_state IN ('RESERVED','CALLING')",
                    state.name(),sql(now()),claim.eventId(),claim.leaseToken());
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void prepared(VisionFallbackJob claim,String eventId,boolean assess,String reason) {
        if (jdbc.update("UPDATE vision_fallback_jobs SET processing_state=?,event_id=?,assessment_required=?,reason=?,finished_at=?,lease_until=CASE WHEN ? THEN lease_until ELSE NULL END WHERE session_id=? AND processing_state='RUNNING' AND lease_token=? AND lease_until>?",
                assess ? "PREPARED" : "SUCCEEDED",eventId,assess,reason,assess ? null : sql(now()),assess,claim.sessionId(),claim.leaseToken(),sql(now()))!=1)
            throw new IllegalStateException("STALE_FALLBACK_PREPARATION");
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean startCalling(VisionFallbackJob claim,VisionFallbackPolicy policy) {
        lockSession(claim.sessionId());
        var locked = jdbc.query("SELECT * FROM vision_fallback_jobs WHERE session_id=? FOR UPDATE",this::map,claim.sessionId()).getFirst();
        if (locked.state()!=VisionFallbackJob.State.PREPARED || !owns(claim) || !eligible(claim.sessionId())) return false;
        var event = jdbc.queryForMap("SELECT event_id,status FROM security_events WHERE event_id=? FOR UPDATE",locked.eventId());
        if ("COMPLETED".equals(event.get("status"))) {
            finish(claim,VisionFallbackJob.State.SKIPPED,"EXISTING_EVENT_COMPLETED"); return false;
        }
        if (!jdbc.queryForList("SELECT event_id FROM security_agent_executions WHERE event_id=?",String.class,locked.eventId()).isEmpty()) {
            finish(claim,VisionFallbackJob.State.UNCERTAIN,"EVENT_AGENT_ALREADY_OWNED"); return false;
        }
        jdbc.update("INSERT INTO security_agent_executions (event_id,execution_token,execution_state,created_at,updated_at) VALUES (?,?,'RESERVED',?,?)",locked.eventId(),claim.leaseToken(),sql(now()),sql(now()));
        jdbc.update("UPDATE vision_fallback_jobs SET processing_state='CALLING',agent_attempts=1,response_state='OUTCOME_UNKNOWN',lease_until=? WHERE session_id=?",sql(now().plus(policy.agentLease())),claim.sessionId());
        return true;
    }
    @Override public boolean isCalling(VisionFallbackJob claim) {
        return find(claim.sessionId()).filter(j -> j.state()==VisionFallbackJob.State.CALLING && Objects.equals(j.leaseToken(),claim.leaseToken()) && j.leaseUntil().isAfter(now())).isPresent();
    }
    @Override public List<VisionFallbackJob> interrupted(int limit) {
        return jdbc.query("""
                SELECT b.* FROM vision_fallback_jobs b WHERE (b.processing_state='CALLING' AND b.lease_until<=?)
                  OR (b.processing_state='UNCERTAIN' AND EXISTS (SELECT 1 FROM security_events e WHERE e.event_id=b.event_id
                    AND e.status='COMPLETED' AND e.risk_level IS NOT NULL AND e.risk_score IS NOT NULL AND e.agent_reason IS NOT NULL))
                ORDER BY b.created_at,b.session_id LIMIT ?
                """,this::map,sql(now()),limit);
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void reconcile(VisionFallbackJob claim, boolean assessed) {
        jdbc.update("UPDATE vision_fallback_jobs SET processing_state=?,reason=?,response_state='UNKNOWN_NOT_REPLAYED',lease_until=NULL,finished_at=? WHERE session_id=? AND lease_token=? AND ((processing_state='CALLING' AND lease_until<=?) OR processing_state='UNCERTAIN')",
                assessed ? "SUCCEEDED" : "UNCERTAIN",assessed ? "RECOVERED_ASSESSMENT" : "AGENT_OUTCOME_UNKNOWN",sql(now()),claim.sessionId(),claim.leaseToken(),sql(now()));
        jdbc.update("UPDATE security_agent_executions SET execution_state=?,updated_at=? WHERE event_id=? AND execution_token=? AND execution_state IN ('RESERVED','CALLING','UNCERTAIN')",
                assessed ? "SUCCEEDED" : "UNCERTAIN",sql(now()),claim.eventId(),claim.leaseToken());
    }
    private VisionFallbackJob map(ResultSet rs,int n) throws SQLException {
        return new VisionFallbackJob(rs.getString("session_id"),VisionFallbackJob.State.valueOf(rs.getString("processing_state")),rs.getString("reason"),rs.getInt("attempts"),rs.getInt("vision_attempts"),rs.getInt("agent_attempts"),
                instant(rs,"video_deadline_at"),rs.getString("lease_token"),instant(rs,"lease_until"),rs.getString("vision_json"),rs.getString("event_id"),rs.getObject("assessment_required",Boolean.class));
    }
    private Instant now() { return visionStreamingClock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static LocalDateTime sql(Instant i) { return LocalDateTime.ofInstant(i,ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs,String field) throws SQLException { var t=rs.getObject(field,LocalDateTime.class); return t==null ? null : t.toInstant(ZoneOffset.UTC); }
}
