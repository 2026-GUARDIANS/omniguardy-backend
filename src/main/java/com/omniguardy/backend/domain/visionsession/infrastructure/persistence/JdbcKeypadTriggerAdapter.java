package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.visionsession.application.port.out.KeypadTriggerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class JdbcKeypadTriggerAdapter implements KeypadTriggerPort {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String triggerId, OffsetDateTime triggeredAt) {
        jdbc.update("INSERT INTO keypad_camera_triggers (trigger_id, triggered_at) VALUES (?, ?)",
                triggerId, triggeredAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }

    @Override
    public boolean exists(String triggerId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM keypad_camera_triggers WHERE trigger_id = ?", Boolean.class, triggerId));
    }
}
