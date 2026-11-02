package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.visionsession.application.port.out.VisionSessionRepository;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionSessionException;
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
public class JdbcVisionSessionRepository implements VisionSessionRepository {
    private final JdbcTemplate jdbc;

    @Override
    public Optional<VisionSession> findBySessionId(String sessionId) {
        return jdbc.query("SELECT * FROM vision_sessions WHERE session_id = ?", (rs, row) ->
                new VisionSession(rs.getString("session_id"), VisionTriggerType.valueOf(rs.getString("trigger_type")),
                        rs.getString("event_id"), rs.getString("trigger_id"),
                        VisionSessionStatus.valueOf(rs.getString("status")),
                        rs.getObject("registered_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), sessionId)
                .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(VisionSession session) {
        try {
            jdbc.update("""
                    INSERT INTO vision_sessions (session_id, trigger_type, event_id, trigger_id, status, registered_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, session.sessionId(), session.triggerType().name(), session.eventId(), session.triggerId(),
                    session.status().name(), LocalDateTime.ofInstant(session.registeredAt(), ZoneOffset.UTC));
        } catch (DuplicateKeyException exception) {
            throw new DuplicateVisionSessionException(exception);
        }
    }
}
