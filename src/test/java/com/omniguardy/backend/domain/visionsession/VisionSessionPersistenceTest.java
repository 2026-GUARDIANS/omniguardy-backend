package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.detection.application.port.out.CameraCommandPort;
import com.omniguardy.backend.domain.detection.application.usecase.TriggerKeypadCameraService;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.application.port.in.VisionSessionInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.VisionSessionService;
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
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisionSessionPersistenceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    VisionSessionInputPort service;
    VisionSessionRepository sessions;
    TransactionTemplate transaction;
    java.sql.Connection schemaConnection;

    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE security_events (event_id VARCHAR(36) NOT NULL UNIQUE)");
        String ddl = Files.readString(Path.of("src/main/resources/db/migration/V6__create_vision_sessions.sql"));
        // H2 does not implement MySQL's charset/collation clause. All other DDL is unchanged.
        ddl = ddl.replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "");
        // H2 2.4 CHECK IN expressions retain their creating session. Keep that
        // connection open while independent connections exercise concurrent inserts.
        schemaConnection = context.getBean(DataSource.class).getConnection();
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ByteArrayResource(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .populate(schemaConnection);
        transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.executeWithoutResult(status -> context.getBean(KeypadTriggerPort.class).record("trigger", OffsetDateTime.now()));
        service = context.getBean(VisionSessionInputPort.class);
        sessions = context.getBean(VisionSessionRepository.class);
    }

    @AfterEach void close() throws Exception {
        if (schemaConnection != null) schemaConnection.close();
        if (context != null) context.close();
    }

    @Test void storesBindingAndReplaysAfterServiceRecreationWithSameTimestamp() {
        var first = service.register("session", KEYPAD, null, "trigger");
        var recreated = new VisionSessionService(sessions, context.getBean(KeypadTriggerPort.class),
                context.getBean(SecurityEventRepository.class));
        assertEquals(first.session(), recreated.find("session"));
        var replay = recreated.register("session", KEYPAD, null, "trigger");
        assertFalse(replay.created()); assertEquals(first.session(), replay.session());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
    }

    @Test void identicalConcurrentRequestsCreateExactlyOneSession() throws Exception {
        var results = concurrent("session", "session");
        assertEquals(1, results.stream().filter(VisionSessionInputPort.Registration::created).count());
        assertEquals(results.getFirst().session(), results.getLast().session());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_sessions", Integer.class));
    }

    @Test void competingSessionIdsForOneTriggerProduceOneConflict() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Object> first = () -> attempt(barrier, "first");
            Callable<Object> second = () -> attempt(barrier, "second");
            var futures = pool.invokeAll(List.of(first, second));
            var results = List.of(futures.getFirst().get(), futures.getLast().get());
            assertEquals(1, results.stream().filter(VisionSessionInputPort.Registration.class::isInstance).count());
            assertEquals(1, results.stream().filter(BusinessException.class::isInstance).count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_sessions", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    private Object attempt(CyclicBarrier barrier, String id) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try { return service.register(id, KEYPAD, null, "trigger"); }
        catch (BusinessException conflict) {
            assertEquals("VISION_SESSION_CONFLICT", conflict.getErrorCode().getCode());
            return conflict;
        }
    }

    private List<VisionSessionInputPort.Registration> concurrent(String firstId, String secondId) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            var futures = pool.invokeAll(List.<Callable<VisionSessionInputPort.Registration>>of(
                    () -> { barrier.await(5, TimeUnit.SECONDS); return service.register(firstId, KEYPAD, null, "trigger"); },
                    () -> { barrier.await(5, TimeUnit.SECONDS); return service.register(secondId, KEYPAD, null, "trigger"); }));
            return List.of(futures.getFirst().get(), futures.getLast().get());
        } finally { pool.shutdownNow(); }
    }

    @Test void databaseEnforcesForeignKeysExclusiveBindingAndBothUniqueReferences() {
        assertThrows(DataIntegrityViolationException.class,
                () -> sessions.insert(VisionSession.register("missing", AUDIO, "event", null)));
        jdbc.update("INSERT INTO security_events VALUES ('event')");
        sessions.insert(VisionSession.register("audio", AUDIO, "event", null));
        assertThrows(com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionSessionException.class,
                () -> sessions.insert(VisionSession.register("audio-duplicate", AUDIO, "event", null)));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO vision_sessions VALUES ('invalid', 'KEYPAD', 'event', 'trigger', 'REGISTERED', CURRENT_TIMESTAMP)
                """));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO vision_sessions VALUES ('invalid', 'KEYPAD', NULL, 'trigger', 'UNKNOWN', CURRENT_TIMESTAMP)
                """));
        assertThrows(DataIntegrityViolationException.class,
                () -> sessions.insert(VisionSession.register("keypad", KEYPAD, null, "not-issued")));
    }

    @Test void mqttFailureRollsBackIssuedKeypadTriggerAndSuccessRetainsIt() {
        var camera = context.getBean(CameraCommandPort.class);
        var triggerService = context.getBean(com.omniguardy.backend.domain.detection.application.port.in.TriggerKeypadCameraUseCase.class);
        doThrow(new IllegalStateException("MQTT unavailable")).when(camera).startCamera(any());
        assertThrows(IllegalStateException.class, triggerService::trigger);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM keypad_camera_triggers", Integer.class));
        reset(camera);
        triggerService.trigger();
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM keypad_camera_triggers", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
    }

    @Configuration @EnableTransactionManagement
    @Import({JdbcVisionSessionRepository.class, JdbcKeypadTriggerAdapter.class,
            VisionSessionService.class, TriggerKeypadCameraService.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager manager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SecurityEventRepository events() { return mock(SecurityEventRepository.class); }
        @Bean CameraCommandPort camera() { return mock(CameraCommandPort.class); }
    }
}
