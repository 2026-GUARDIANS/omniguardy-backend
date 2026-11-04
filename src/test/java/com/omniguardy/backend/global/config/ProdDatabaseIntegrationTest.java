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
        "omniguardy.vision-fallback.enabled=false",
        "omniguardy.streaming-video.event-link-enabled=false",
        "FIREBASE_CREDENTIALS_PATH=/not-used-mocked.json", "AWS_REGION=ap-northeast-2", "S3_BUCKET_NAME=test-bucket"
})
class ProdDatabaseIntegrationTest {
    @MockitoBean FirebaseConfig firebase;
    @MockitoBean StreamingVisionQueryPort streamingVision;
    @MockitoBean com.omniguardy.backend.domain.securityevent.application.port.out.EarlyWarningDeliveryPort earlyWarningDelivery;
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
    @Autowired com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVideoRepository streamingVideos;
    @Autowired com.omniguardy.backend.domain.visionsession.application.port.out.VisionFallbackRepository fallbackJobs;
    @Autowired com.omniguardy.backend.domain.visionsession.application.model.VisionFallbackPolicy fallbackPolicy;
    @Autowired com.omniguardy.backend.domain.securityevent.application.port.out.AgentExecutionGuard agentGuard;
    @Autowired com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository securityEvents;
    @Autowired com.omniguardy.backend.domain.visionsession.application.port.in.LinkStreamingVideoInputPort videoLinks;

    @Test void mySqlVideoAttachmentSurvivesStaleManagedSaveAndConcurrentRetries() throws Exception {
        String session = "link-" + java.util.UUID.randomUUID(); String id = java.util.UUID.randomUUID().toString();
        String key = "videos/" + session + ".mp4";
        var pool = Executors.newFixedThreadPool(2);
        try {
            securityEvents.save(com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent.builder()
                    .eventId(id).triggerId(session).triggerType(com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType.AUDIO)
                    .triggeredAt(OffsetDateTime.now()).status("COMPLETED").build());
            sessions.insert(VisionSession.register(session, com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.AUDIO, id, null));
            var info = new com.omniguardy.backend.domain.visionsession.infrastructure.video.Mp4VideoValidator()
                    .validate(com.omniguardy.backend.domain.visionsession.StreamingMp4TestSupport.file(1));
            var binding = new com.omniguardy.backend.domain.visionsession.domain.model.VisionBinding(session,
                    com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.AUDIO, session, id);
            var target = new com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget("S3", "test-bucket", session + ".mp4", key);
            streamingVideos.complete(streamingVideos.reserve(binding, info, target, Duration.ofMinutes(5)));
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                var stale = securityEvents.findByEventId(id).orElseThrow(); assertNull(stale.getVideoPath());
                try { pool.submit(() -> videoLinks.afterUpload(session)).get(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError(failure); }
                stale.updateAgentResult("MEDIUM", 42, "synthetic assessment"); securityEvents.save(stale);
            });
            var first = pool.submit(() -> videoLinks.afterUpload(session));
            var second = pool.submit(() -> videoLinks.afterUpload(session));
            first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
            var event = securityEvents.findByEventId(id).orElseThrow();
            assertEquals(key, event.getVideoPath()); assertEquals("COMPLETED", event.getStatus()); assertEquals(42, event.getRiskScore());
            assertEquals(com.omniguardy.backend.domain.securityevent.domain.model.VideoLinkResult.CONFLICT,
                    securityEvents.linkVideo(id, event.getTriggerType(), session, "videos/different.mp4"));
            assertEquals(key, securityEvents.findByEventId(id).orElseThrow().getVideoPath());
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM streaming_session_videos WHERE session_id=?", session);
            jdbc.update("DELETE FROM vision_sessions WHERE session_id=?", session);
            jdbc.update("DELETE FROM security_events WHERE event_id=?", id);
        }
    }

    @Test void mySqlFallbackAndAgentReservationsSurviveConcurrentDiscoveryAndRestart() throws Exception {
        String marker="fallback-"+java.util.UUID.randomUUID(); String eventId=java.util.UUID.randomUUID().toString();
        var pool=Executors.newFixedThreadPool(4);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> keypadTriggers.record(marker,OffsetDateTime.now()));
            sessions.insert(VisionSession.register(marker,KEYPAD,null,marker));
            pollingJobs.enqueue(new com.omniguardy.backend.domain.visionsession.domain.model.VisionBinding(marker,KEYPAD,marker,null),Duration.ofMinutes(5));
            pollingJobs.finish(pollingJobs.claim(marker,Duration.ofSeconds(30)).orElseThrow(),result(marker,marker,"KEYPAD",null),VisionFinalOutcome.VALID,"test",null,null);
            streamingAgentJobs.enqueue(marker);
            var v9=streamingAgentJobs.claim(marker,Duration.ofSeconds(30)).orElseThrow();
            streamingAgentJobs.finish(v9,com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentJob.State.BLOCKED,"UNKNOWN_PERSON_COUNT","NOT_STARTED");
            var discover=new ArrayList<Callable<Void>>();
            for(int i=0;i<4;i++) discover.add(() -> { fallbackJobs.discover(fallbackPolicy); return null; });
            for(var future:pool.invokeAll(discover,30,TimeUnit.SECONDS)) future.get();
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM vision_fallback_jobs WHERE session_id=?",Integer.class,marker));
            assertTrue(streamingAgentJobs.claim(marker,Duration.ofSeconds(30)).isEmpty());
            var tasks=new ArrayList<Callable<Optional<com.omniguardy.backend.domain.visionsession.domain.model.VisionFallbackJob>>>();
            for(int i=0;i<4;i++) tasks.add(() -> fallbackJobs.claim(marker,fallbackPolicy));
            var claims=new ArrayList<com.omniguardy.backend.domain.visionsession.domain.model.VisionFallbackJob>();
            for(var future:pool.invokeAll(tasks,30,TimeUnit.SECONDS)) future.get().ifPresent(claims::add);
            assertEquals(1,claims.size()); var claim=claims.getFirst();
            jdbc.update("INSERT INTO security_events (created_at,updated_at,event_id,trigger_id,trigger_type,triggered_at,status) VALUES (NOW(6),NOW(6),?,?,'KEYPAD',NOW(6),'VISION_ANALYZED')",eventId,marker);
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> fallbackJobs.prepared(claim,eventId,true,"AGENT_READY"));
            var prepared=fallbackJobs.find(marker).orElseThrow();
            var calls=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<4;i++) calls.add(() -> fallbackJobs.startCalling(prepared,fallbackPolicy));
            int winners=0; for(var future:pool.invokeAll(calls,30,TimeUnit.SECONDS)) if(future.get()) winners++;
            assertEquals(1,winners);
            assertThrows(com.omniguardy.backend.domain.securityevent.domain.error.AgentExecutionConflict.class,() -> agentGuard.begin(eventId,null));
            assertNotNull(agentGuard.begin(eventId,prepared.leaseToken()));
            assertThrows(com.omniguardy.backend.domain.securityevent.domain.error.AgentExecutionConflict.class,() -> agentGuard.begin(eventId,prepared.leaseToken()));
            jdbc.update("UPDATE vision_fallback_jobs SET lease_until='2000-01-01 00:00:00' WHERE session_id=?",marker);
            assertTrue(fallbackJobs.claim(marker,fallbackPolicy).isEmpty());
            fallbackJobs.reconcile(fallbackJobs.find(marker).orElseThrow(),false);
            assertEquals("UNCERTAIN",fallbackJobs.find(marker).orElseThrow().state().name());
            assertEquals("UNCERTAIN",jdbc.queryForObject("SELECT execution_state FROM security_agent_executions WHERE event_id=?",String.class,eventId));
            assertEquals("BLOCKED",streamingAgentJobs.find(marker).orElseThrow().state().name());
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM security_agent_executions WHERE event_id=?",eventId);
            jdbc.update("DELETE FROM vision_fallback_jobs WHERE session_id=?",marker);
            jdbc.update("DELETE FROM streaming_agent_jobs WHERE session_id=?",marker);
            jdbc.update("DELETE FROM vision_final_results WHERE session_id=?",marker);
            jdbc.update("DELETE FROM vision_polling_jobs WHERE session_id=?",marker);
            jdbc.update("DELETE FROM vision_sessions WHERE session_id=?",marker);
            jdbc.update("DELETE FROM keypad_camera_triggers WHERE trigger_id=?",marker);
            jdbc.update("DELETE FROM security_events WHERE event_id=?",eventId);
        }
    }
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
        assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_fallback_jobs", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM streaming_session_videos", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM streaming_agent_jobs", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_polling_jobs", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_final_results", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM video_uploads", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_sessions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM keypad_camera_triggers", Integer.class));
    }

    @Test void mySqlVideoClaimsAreUniqueAndStorageStateSurvivesRetries() throws Exception {
        String marker = "video-" + java.util.UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(4);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> keypadTriggers.record(marker, OffsetDateTime.now()));
            sessions.insert(VisionSession.register(marker, KEYPAD, null, marker));
            var info = new com.omniguardy.backend.domain.visionsession.infrastructure.video.Mp4VideoValidator()
                    .validate(com.omniguardy.backend.domain.visionsession.StreamingMp4TestSupport.file(1));
            var target = new com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget("S3", "bucket", "test.mp4", "s3://bucket/test.mp4");
            var binding = new com.omniguardy.backend.domain.visionsession.domain.model.VisionBinding(marker, KEYPAD, marker, null);
            var barrier = new CyclicBarrier(4);
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 4; i++) tasks.add(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try { streamingVideos.reserve(binding, info, target, Duration.ofMinutes(5)); return true; }
                catch (com.omniguardy.backend.global.error.exception.BusinessException conflict) {
                    assertEquals("STREAMING_VIDEO_IN_PROGRESS", conflict.getErrorCode().getCode()); return false;
                }
            });
            int winners = 0;
            for (var future : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) if (future.get()) winners++;
            assertEquals(1, winners);
            var first = streamingVideos.find(marker).orElseThrow();
            assertTrue(streamingVideos.find(marker.toUpperCase(java.util.Locale.ROOT)).isEmpty());
            streamingVideos.failed(first, "STORAGE_FAILED");
            var retry = streamingVideos.reserve(binding, info, target, Duration.ofMinutes(5));
            assertEquals(2, retry.attempts());
            streamingVideos.complete(retry);
            assertEquals("STORED", streamingVideos.reserve(binding, info, target, Duration.ofMinutes(5)).state().name());
            assertThrows(com.omniguardy.backend.global.error.exception.BusinessException.class, () -> streamingVideos.complete(first));
            var rejected = assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> jdbc.update("UPDATE streaming_session_videos SET upload_state='UPLOADING' WHERE session_id=?", marker));
            assertTrue(rejected.getMostSpecificCause().getMessage().contains("ck_streaming_video_state"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM streaming_session_videos WHERE session_id=?", Integer.class, marker));
            assertEquals("REGISTERED", sessions.findBySessionId(marker).orElseThrow().status().name());
        } finally {
            pool.shutdownNow();
            jdbc.update("DELETE FROM streaming_session_videos WHERE session_id=?", marker);
            jdbc.update("DELETE FROM vision_sessions WHERE session_id=?", marker);
            jdbc.update("DELETE FROM keypad_camera_triggers WHERE trigger_id=?", marker);
        }
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
