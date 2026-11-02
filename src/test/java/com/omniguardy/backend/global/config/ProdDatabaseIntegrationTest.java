package com.omniguardy.backend.global.config;

import com.omniguardy.backend.BackendApplication;
import com.omniguardy.backend.domain.detection.application.port.out.MediaStoragePort;
import com.omniguardy.backend.domain.detection.infrastructure.storage.S3MediaStorageAdapter;
import com.omniguardy.backend.domain.tts.application.port.out.TtsDeliveryPort;
import com.omniguardy.backend.domain.tts.infrastructure.delivery.DisabledTtsAdapter;
import com.omniguardy.backend.domain.visionsession.application.port.in.ReceiveVisionEarlyEventInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.KeypadTriggerPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.VisionSessionRepository;
import com.omniguardy.backend.domain.visionsession.application.port.out.VisionPollingRepository;
import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVisionQueryPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingAgentRepository;
import com.omniguardy.backend.domain.visionsession.application.port.in.CompleteVisionSessionInputPort;
import com.omniguardy.backend.domain.visionsession.application.usecase.PollStreamingVisionService;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionFinalOutcome;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionEarlyEvent;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.time.Duration;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.KEYPAD;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;

/** Only run against a newly created disposable MySQL container, never an existing database. */
@EnabledIfEnvironmentVariable(named = "OMNIGUARDY_TEST_MYSQL_URL", matches = ".+")
@SpringBootTest(classes = BackendApplication.class, properties = {
        "spring.profiles.active=prod",
        "DB_URL=${OMNIGUARDY_TEST_MYSQL_URL}", "DB_USERNAME=validation", "DB_PASSWORD=disposable-test-only",
        "JWT_SECRET_KEY=unit-test-only-secret-with-at-least-32-bytes",
        "MQTT_BROKER_URL=tcp://127.0.0.1:1", "MQTT_USERNAME=backend", "MQTT_PASSWORD=disposable-test-only",
        "AUDIO_DEVICE_API_KEY=unit-test-only", "OMNIGUARDY_DEMO_USERID=1", "AGENT_AI_ENABLED=false",
        "AI_FASTAPI_AUDIO_BASE_URL=https://audio.invalid", "AI_FASTAPI_VISION_BASE_URL=https://vision.invalid",
        "ai.fastapi.streaming-vision.polling-enabled=false",
        "FIREBASE_CREDENTIALS_PATH=/not-used-mocked.json", "AWS_REGION=ap-northeast-2", "S3_BUCKET_NAME=test-bucket"
})
class ProdDatabaseIntegrationTest {
    @MockitoBean FirebaseConfig firebase;
    @MockitoBean StreamingVisionQueryPort streamingVision;
    @Autowired Environment env;
    @Autowired MediaStoragePort storage;
    @Autowired TtsDeliveryPort tts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceiveVisionEarlyEventInputPort earlyEvents;
    @Autowired VisionSessionRepository sessions;
    @Autowired KeypadTriggerPort keypadTriggers;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CompleteVisionSessionInputPort completion;
    @Autowired VisionPollingRepository pollingJobs;
    @Autowired PollStreamingVisionService pollingWorker;
    @Autowired StreamingAgentRepository streamingAgentJobs;
    @Test void additiveBaseMigrationPreservesExistingRows() {
        String marker = java.util.UUID.randomUUID().toString().substring(0, 16);
        jdbc.update("INSERT INTO users (created_at, updated_at, email, password, name, phone_number) VALUES (NOW(6),NOW(6),?,'synthetic','test',?)", marker + "@invalid", marker);
        var populator = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V4__create_missing_base_tables.sql"));
        populator.execute(java.util.Objects.requireNonNull(jdbc.getDataSource()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, marker + "@invalid"));
    }
    @Test void emptyMySqlMigratesAndHibernateValidatesWithProdMappings() {
        assertInstanceOf(S3MediaStorageAdapter.class, storage);
        assertInstanceOf(DisabledTtsAdapter.class, tts);
        assertEquals("unit-test-only-secret-with-at-least-32-bytes", env.getProperty("jwt.secret-key"));
        assertEquals("https://vision.invalid", env.getProperty("ai.fastapi.vision.base-url"));
        assertEquals("https://audio.invalid", env.getProperty("ai.fastapi.audio.base-url"));
        assertEquals("180", env.getProperty("ai.fastapi.vision.read-timeout-seconds"));
        assertEquals(9, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM streaming_agent_jobs", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_polling_jobs", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_final_results", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM video_uploads", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_sessions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM keypad_camera_triggers", Integer.class));
    }

    @Test void mySqlConcurrentCallbackRetriesCommitOnceAndIdentifiersRemainCaseSensitive() throws Exception {
        String marker = "early-" + java.util.UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(4);
        try {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> keypadTriggers.record(marker, OffsetDateTime.now()));
            sessions.insert(VisionSession.register(marker, KEYPAD, null, marker));
            var callback = new VisionEarlyEvent(marker, marker, null, KEYPAD,
                    "signal", 1, 114, 114, "3.800000", "2026-11-02T14:44:19.167+09:00");
            var barrier = new CyclicBarrier(4);
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 4; i++) tasks.add(() -> {
                barrier.await(10, TimeUnit.SECONDS); earlyEvents.receive(callback); return null;
            });
            for (var future : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) future.get();
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events WHERE session_id = ?", Integer.class, marker));
            earlyEvents.receive(new VisionEarlyEvent(marker, marker, null, KEYPAD,
                    "SIGNAL", 1, 114, 114, null, callback.emittedAt()));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events WHERE session_id = ?", Integer.class, marker));
            assertEquals("REGISTERED", sessions.findBySessionId(marker).orElseThrow().status().name());
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM vision_early_events WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_sessions WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM keypad_camera_triggers WHERE trigger_id = ?", marker);
        }
    }

    @Test void mySqlCompletionAndFinalResultAreIdempotentAndAtomic() throws Exception {
        String marker = "final-" + java.util.UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(4);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> keypadTriggers.record(marker, OffsetDateTime.now()));
            sessions.insert(VisionSession.register(marker, KEYPAD, null, marker));
            var tasks = new ArrayList<Callable<String>>();
            var barrier = new CyclicBarrier(4);
            for (int i = 0; i < 4; i++) tasks.add(() -> {
                barrier.await(10, TimeUnit.SECONDS); return completion.complete(marker).sessionId();
            });
            for (var future : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) assertEquals(marker, future.get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_polling_jobs WHERE session_id = ?", Integer.class, marker));
            var claim = pollingJobs.claim(marker, Duration.ofSeconds(30)).orElseThrow();
            var finalResult = result(marker, marker, "KEYPAD", null);
            // A failure after result insertion and session update must roll the entire operation back.
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> pollingJobs.finish(
                    claim, finalResult, VisionFinalOutcome.VALID, "x".repeat(65), null, null));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_final_results WHERE session_id = ?", Integer.class, marker));
            assertEquals("REGISTERED", sessions.findBySessionId(marker).orElseThrow().status().name());
            pollingJobs.finish(claim, finalResult, VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
            pollingJobs.finish(claim, finalResult, VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
            assertEquals("SUCCEEDED", completion.complete(marker).processingState().name());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_final_results WHERE session_id = ?", Integer.class, marker));
            assertEquals("COMPLETED", sessions.findBySessionId(marker).orElseThrow().status().name());
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM vision_final_results WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_polling_jobs WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_sessions WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM keypad_camera_triggers WHERE trigger_id = ?", marker);
        }
    }

    @Test void mySqlWorkerStoresFinalResultWithoutChangingEarlyEventsOrSecurityEvents() {
        String marker = "worker-" + java.util.UUID.randomUUID();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> keypadTriggers.record(marker, OffsetDateTime.now()));
            sessions.insert(VisionSession.register(marker, KEYPAD, null, marker));
            when(streamingVision.status(marker)).thenReturn(status(marker, marker, "KEYPAD", null));
            when(streamingVision.result(marker)).thenReturn(Optional.of(result(marker, marker, "KEYPAD", null)));
            completion.complete(marker); pollingWorker.pollDue(); pollingWorker.pollDue();
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_final_results WHERE session_id = ?", Integer.class, marker));
            assertEquals("COMPLETED", sessions.findBySessionId(marker).orElseThrow().status().name());
            verify(streamingVision, times(1)).status(marker);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events WHERE session_id = ?", Integer.class, marker));
        } finally {
            jdbc.update("DELETE FROM vision_final_results WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_polling_jobs WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_sessions WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM keypad_camera_triggers WHERE trigger_id = ?", marker);
        }
    }

    @Test void mySqlAgentLedgerAllowsOneClaimAndNeverReclaimsCallingAttempt() throws Exception {
        String marker = "agent-" + java.util.UUID.randomUUID();
        String eventId = java.util.UUID.randomUUID().toString();
        var pool = Executors.newFixedThreadPool(4);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> keypadTriggers.record(marker, OffsetDateTime.now()));
            sessions.insert(VisionSession.register(marker, KEYPAD, null, marker));
            pollingJobs.enqueue(new com.omniguardy.backend.domain.visionsession.domain.model.VisionBinding(marker, KEYPAD, marker, null), Duration.ofMinutes(5));
            var pollingClaim = pollingJobs.claim(marker, Duration.ofSeconds(30)).orElseThrow();
            pollingJobs.finish(pollingClaim, result(marker, marker, "KEYPAD", null), VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
            var enqueueTasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 4; i++) enqueueTasks.add(() -> { streamingAgentJobs.enqueue(marker); return null; });
            for (var future : pool.invokeAll(enqueueTasks, 30, TimeUnit.SECONDS)) future.get();
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM streaming_agent_jobs WHERE session_id = ?", Integer.class, marker));
            var tasks = new ArrayList<Callable<Optional<com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentJob>>>();
            for (int i = 0; i < 4; i++) tasks.add(() -> streamingAgentJobs.claim(marker, Duration.ofSeconds(30)));
            var claims = new ArrayList<com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentJob>();
            for (var future : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) future.get().ifPresent(claims::add);
            assertEquals(1, claims.size()); var claim = claims.getFirst();
            jdbc.update("INSERT INTO security_events (created_at, updated_at, event_id, trigger_id, trigger_type, triggered_at, status) VALUES (NOW(6), NOW(6), ?, ?, 'KEYPAD', NOW(6), 'VISION_ANALYZED')", eventId, marker);
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                assertTrue(streamingAgentJobs.lockPreparation(claim)); streamingAgentJobs.prepared(claim, eventId);
            });
            var prepared = streamingAgentJobs.find(marker).orElseThrow();
            assertTrue(streamingAgentJobs.startCalling(prepared, Duration.ofSeconds(30)));
            jdbc.update("UPDATE streaming_agent_jobs SET lease_until = '2000-01-01 00:00:00' WHERE session_id = ?", marker);
            assertTrue(streamingAgentJobs.claim(marker, Duration.ofSeconds(30)).isEmpty());
            streamingAgentJobs.reconcile(streamingAgentJobs.find(marker).orElseThrow(), false);
            assertEquals("UNCERTAIN", streamingAgentJobs.find(marker).orElseThrow().state().name());
            assertFalse(streamingAgentJobs.due(100).contains(marker));
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM streaming_agent_jobs WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_final_results WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_polling_jobs WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM vision_sessions WHERE session_id = ?", marker);
            jdbc.update("DELETE FROM keypad_camera_triggers WHERE trigger_id = ?", marker);
            jdbc.update("DELETE FROM security_events WHERE event_id = ?", eventId);
        }
    }
}
