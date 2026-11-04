package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.visionsession.application.port.in.ReceiveVisionEarlyEventInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.ReceiveVisionEarlyEventService;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionEarlyEventException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.persistence.*;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisionEarlyEventPersistenceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    ReceiveVisionEarlyEventInputPort service;
    VisionEarlyEventRepository events;
    java.sql.Connection schemaConnection;
    org.springframework.transaction.support.TransactionTemplate transaction;

    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE security_events (event_id VARCHAR(36) NOT NULL UNIQUE)");
        schemaConnection = context.getBean(DataSource.class).getConnection();
        for (String migration : List.of("V6__create_vision_sessions.sql", "V7__create_vision_early_events.sql")) {
            String ddl = Files.readString(Path.of("src/main/resources/db/migration/" + migration))
                    .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "");
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ByteArrayResource(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .populate(schemaConnection);
        }
        transaction = new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> context.getBean(KeypadTriggerPort.class).record("trigger", OffsetDateTime.now()));
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("session", KEYPAD, null, "trigger"));
        service = context.getBean(ReceiveVisionEarlyEventInputPort.class);
        events = context.getBean(VisionEarlyEventRepository.class);
    }

    @AfterEach void close() throws Exception {
        if (schemaConnection != null) schemaConnection.close();
        if (context != null) context.close();
    }

    @Test void roundTripAndRetryAfterServiceRecreationPreserveOriginalRecordAndSession() {
        var event = VisionEarlyEventServiceTest.event(KEYPAD, "trigger", null, "one");
        var session = context.getBean(VisionSessionRepository.class).findBySessionId("session");
        service.receive(event);
        var received = jdbc.queryForObject("SELECT received_at FROM vision_early_events", java.time.LocalDateTime.class);
        var recreated = new ReceiveVisionEarlyEventService(context.getBean(VisionSessionRepository.class),
                context.getBean(KeypadTriggerPort.class), context.getBean(VisionAudioTriggerPort.class), events,
                context.getBean(VisionEarlyWarningPort.class));
        recreated.receive(event);
        assertEquals(event, events.find("session", "one").orElseThrow());
        assertEquals(received, jdbc.queryForObject("SELECT received_at FROM vision_early_events", java.time.LocalDateTime.class));
        assertEquals(session, context.getBean(VisionSessionRepository.class).findBySessionId("session"));
        assertEquals(1, count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
        assertEquals("EARLY_EVENT", jdbc.queryForObject("SELECT message_type FROM vision_early_events", String.class));
        assertEquals(true, jdbc.queryForObject("SELECT provisional FROM vision_early_events", Boolean.class));
    }

    @Test void concurrentIdenticalCallbacksAllSucceedAndCommitExactlyOneRow() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var barrier = new CyclicBarrier(8);
            var event = VisionEarlyEventServiceTest.event(KEYPAD, "trigger", null, "one");
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 8; i++) tasks.add(() -> { barrier.await(10, TimeUnit.SECONDS); service.receive(event); return null; });
            for (var future : pool.invokeAll(tasks, 20, TimeUnit.SECONDS)) future.get();
            assertEquals(1, count());
            assertEquals(event, events.find("session", "one").orElseThrow());
        } finally { pool.shutdownNow(); }
    }

    @Test void changedPayloadCannotOverwriteFirstRecord() {
        var first = VisionEarlyEventServiceTest.event(KEYPAD, "trigger", null, "one");
        service.receive(first);
        var changed = new VisionEarlyEvent("session", "trigger", null, KEYPAD, "one", 1, 115, 115,
                "changed", first.emittedAt());
        assertEquals("VISION_EARLY_EVENT_CONFLICT", assertThrows(BusinessException.class,
                () -> service.receive(changed)).getErrorCode().getCode());
        assertEquals(first, events.find("session", "one").orElseThrow());
        assertEquals(1, count());
    }

    @Test void uniquenessIsPerSessionAndInstanceNotSequenceAndIsCaseSensitive() {
        service.receive(VisionEarlyEventServiceTest.event(KEYPAD, "trigger", null, "one"));
        service.receive(VisionEarlyEventServiceTest.event(KEYPAD, "trigger", null, "ONE"));
        service.receive(VisionEarlyEventServiceTest.event(KEYPAD, "trigger", null, "two"));
        transaction.executeWithoutResult(status -> context.getBean(KeypadTriggerPort.class).record("second-trigger", OffsetDateTime.now()));
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("Session", KEYPAD, null, "second-trigger"));
        service.receive(new VisionEarlyEvent("Session", "second-trigger", null, KEYPAD, "one", 1, 114, 114, null,
                "2026-11-02T14:44:19.167+09:00"));
        assertEquals(4, count());
    }

    @Test void databaseEnforcesUniqueForeignKeyAndProvisionalMessageTypeConstraints() {
        var event = VisionEarlyEventServiceTest.event(KEYPAD, "trigger", null, "one");
        events.insert(event);
        assertThrows(DuplicateVisionEarlyEventException.class, () -> events.insert(event));
        assertThrows(DataIntegrityViolationException.class, () -> events.insert(new VisionEarlyEvent(
                "missing", "trigger", null, KEYPAD, "one", 1, 114, 114, null, event.emittedAt())));
        for (String update : List.of("message_type = 'FINAL_RESULT'", "provisional = FALSE",
                "event_type = 'OTHER'", "security_event_id = 'event'")) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE vision_early_events SET " + update));
        }
        assertEquals(1, count());
    }

    @Test void audioAndNullableCaptureRoundTripWithoutNewSecurityEvent() {
        jdbc.update("INSERT INTO security_events VALUES ('event')");
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("audio-session", AUDIO, "event", null));
        when(context.getBean(VisionAudioTriggerPort.class).findIssuedTriggerId("event")).thenReturn(Optional.of("audio-trigger"));
        var event = new VisionEarlyEvent("audio-session", "audio-trigger", "event", AUDIO, "one", 1, 114, 114,
                null, "2026-11-02T05:44:19.167Z");
        service.receive(event); service.receive(event);
        assertEquals(event, events.find("audio-session", "one").orElseThrow());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
    }

    int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class); }

    @Configuration @EnableTransactionManagement
    @Import({JdbcVisionSessionRepository.class, JdbcKeypadTriggerAdapter.class,
            JdbcVisionEarlyEventRepository.class, ReceiveVisionEarlyEventService.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager manager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean VisionAudioTriggerPort audio() { return mock(VisionAudioTriggerPort.class); }
        @Bean VisionEarlyWarningPort warnings() { return mock(VisionEarlyWarningPort.class); }
    }
}
