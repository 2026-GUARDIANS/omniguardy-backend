package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.application.port.out.ServerEventTimingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository @RequiredArgsConstructor
public class JdbcServerEventTimingAdapter implements ServerEventTimingPort {
    private final JdbcTemplate jdbc;
    @Override public Optional<Timing> find(String id) {
        return jdbc.query("""
                SELECT v.session_id, e.trigger_type,
                    CASE WHEN e.trigger_type = 'AUDIO' THEN e.triggered_at ELSE k.triggered_at END AS observed_at
                FROM security_events e
                LEFT JOIN keypad_camera_triggers k ON e.trigger_type = 'KEYPAD' AND k.trigger_id = e.trigger_id
                LEFT JOIN vision_sessions v ON v.trigger_type = e.trigger_type
                    AND ((e.trigger_type = 'AUDIO' AND v.event_id = e.event_id)
                      OR (e.trigger_type = 'KEYPAD' AND v.trigger_id = e.trigger_id))
                WHERE e.event_id = ?
                """, (rs, row) -> {
            var at = rs.getObject("observed_at", LocalDateTime.class);
            return new Timing(rs.getString("session_id"), at == null ? null : at.toInstant(ZoneOffset.UTC),
                    at == null ? "UNAVAILABLE" : "AUDIO".equals(rs.getString("trigger_type"))
                            ? "SERVER_AUDIO_TRIGGER_RECORDED" : "SERVER_KEYPAD_TRIGGER_RECORDED");
        }, id).stream().findFirst();
    }
}
