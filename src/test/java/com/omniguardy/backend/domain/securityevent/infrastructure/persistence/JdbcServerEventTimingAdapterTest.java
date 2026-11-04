package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JdbcServerEventTimingAdapterTest {
    @Test void usesPersistedServerTriggerTimesAndNeverKeypadClientReportedTimestamp() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE security_events(event_id VARCHAR(36), trigger_id VARCHAR(64), trigger_type VARCHAR(20), triggered_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE keypad_camera_triggers(trigger_id VARCHAR(64), triggered_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE vision_sessions(session_id VARCHAR(128), event_id VARCHAR(36), trigger_id VARCHAR(64), trigger_type VARCHAR(20))");
        jdbc.update("INSERT INTO security_events VALUES('audio','audio-trigger','AUDIO','2026-11-04 01:00:00'),('keypad','keypad-trigger','KEYPAD','2020-01-01 00:00:00'),('legacy','missing','KEYPAD','2020-01-01 00:00:00')");
        jdbc.update("INSERT INTO keypad_camera_triggers VALUES('keypad-trigger','2026-11-04 02:00:00')");
        jdbc.update("INSERT INTO vision_sessions VALUES('audio-session','audio',NULL,'AUDIO'),('keypad-session',NULL,'keypad-trigger','KEYPAD')");
        var adapter = new JdbcServerEventTimingAdapter(jdbc);
        assertEquals(Instant.parse("2026-11-04T01:00:00Z"), adapter.find("audio").orElseThrow().observedAt());
        var keypad = adapter.find("keypad").orElseThrow();
        assertEquals("keypad-session", keypad.sessionId()); assertEquals(Instant.parse("2026-11-04T02:00:00Z"), keypad.observedAt());
        assertNull(adapter.find("legacy").orElseThrow().observedAt());
        assertTrue(adapter.find("unknown").isEmpty());
    }
}
