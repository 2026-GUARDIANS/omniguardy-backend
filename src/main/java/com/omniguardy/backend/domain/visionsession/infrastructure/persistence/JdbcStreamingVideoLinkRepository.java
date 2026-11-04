package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVideoLinkRepository;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode.STREAMING_VIDEO_BINDING_MISMATCH;

@Repository @RequiredArgsConstructor
public class JdbcStreamingVideoLinkRepository implements StreamingVideoLinkRepository {
    private final JdbcTemplate jdbc;

    @Override @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<Candidate> findVerified(String id) {
        var rows = jdbc.query("""
                SELECT v.trigger_type, v.trigger_id, v.security_event_id, v.storage_path,
                    s.trigger_type AS session_type, s.event_id, s.trigger_id AS session_trigger,
                    a.event_id AS agent_event, b.event_id AS fallback_event
                FROM streaming_session_videos v JOIN vision_sessions s ON s.session_id = v.session_id
                LEFT JOIN streaming_agent_jobs a ON a.session_id = v.session_id
                LEFT JOIN vision_fallback_jobs b ON b.session_id = v.session_id
                WHERE v.session_id = ? AND v.upload_state = 'STORED'
                """, (rs, row) -> {
            var type = VisionTriggerType.valueOf(rs.getString("trigger_type"));
            String trigger = rs.getString("trigger_id"), original = rs.getString("security_event_id");
            String agent = rs.getString("agent_event"), fallback = rs.getString("fallback_event");
            boolean valid = type.name().equals(rs.getString("session_type"));
            if (type == VisionTriggerType.AUDIO)
                valid &= original != null && original.equals(rs.getString("event_id")) && rs.getString("session_trigger") == null;
            else valid &= original == null && rs.getString("event_id") == null && trigger.equals(rs.getString("session_trigger"));
            String event = original != null ? original : agent != null ? agent : fallback;
            valid &= (agent == null || agent.equals(event)) && (fallback == null || fallback.equals(event));
            if (!valid) throw new BusinessException(STREAMING_VIDEO_BINDING_MISMATCH);
            return new Candidate(id, type, trigger, event, rs.getString("storage_path"));
        }, id);
        return rows.stream().filter(row -> row.eventId() != null).findFirst();
    }

    @Override public List<String> storedSessionsAfter(String id, int limit) {
        return jdbc.queryForList("""
                SELECT session_id FROM streaming_session_videos WHERE upload_state = 'STORED' AND session_id > ?
                ORDER BY session_id LIMIT ?
                """, String.class, id, limit);
    }
}
