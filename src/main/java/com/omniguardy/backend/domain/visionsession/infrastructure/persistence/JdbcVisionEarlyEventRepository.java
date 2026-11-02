package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.visionsession.application.port.out.VisionEarlyEventRepository;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionEarlyEventException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcVisionEarlyEventRepository implements VisionEarlyEventRepository {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(VisionEarlyEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO vision_early_events
                    (session_id, event_instance_id, message_type, trigger_id, security_event_id, trigger_type,
                     event_sequence, provisional, event_type, detected_frame, confirmed_frame, captured_at, emitted_at, received_at)
                    VALUES (?, ?, 'EARLY_EVENT', ?, ?, ?, ?, TRUE, 'SILENT_SIGNAL', ?, ?, ?, ?, ?)
                    """, event.sessionId(), event.eventInstanceId(), event.triggerId(), event.securityEventId(),
                    event.triggerType().name(), event.sequence(), event.detectedFrame(), event.confirmedFrame(),
                    event.capturedAt(), event.emittedAt(), LocalDateTime.now(ZoneOffset.UTC));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateVisionEarlyEventException(exception);
        }
    }

    @Override
    public Optional<VisionEarlyEvent> find(String sessionId, String eventInstanceId) {
        return jdbc.query("SELECT * FROM vision_early_events WHERE session_id = ? AND event_instance_id = ?",
                (rs, row) -> new VisionEarlyEvent(rs.getString("session_id"), rs.getString("trigger_id"),
                        rs.getString("security_event_id"), VisionTriggerType.valueOf(rs.getString("trigger_type")),
                        rs.getString("event_instance_id"), rs.getLong("event_sequence"), rs.getLong("detected_frame"),
                        rs.getLong("confirmed_frame"), rs.getString("captured_at"), rs.getString("emitted_at")),
                sessionId, eventInstanceId).stream().findFirst();
    }
}
