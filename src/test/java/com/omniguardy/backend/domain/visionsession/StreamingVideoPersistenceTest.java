package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.StreamingSessionVideoService;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.persistence.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.video.Mp4VideoValidator;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.StreamingMp4TestSupport.*;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamingVideoPersistenceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    StreamingVideoRepository videos;
    StreamingSessionVideoService service;
    StreamingMediaStoragePort storage;
    MutableClock clock;
    java.sql.Connection schema;
    final Set<String> objects = ConcurrentHashMap.newKeySet();

    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE security_events (event_id VARCHAR(36) NOT NULL UNIQUE, status VARCHAR(30), audio_probability DOUBLE, video_path VARCHAR(255))");
        schema = context.getBean(DataSource.class).getConnection();
        for (String migration : List.of("V6__create_vision_sessions.sql", "V7__create_vision_early_events.sql",
                "V8__create_vision_polling_and_final_results.sql", "V9__create_streaming_agent_jobs.sql", "V10__create_streaming_session_videos.sql", "V11__create_vision_fallback_jobs.sql")) {
            var ddl = Files.readString(Path.of("src/main/resources/db/migration/" + migration))
                    .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "")
                    .replace(" CHARACTER SET ascii COLLATE ascii_bin", "");
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(
                    ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8))).populate(schema);
        }
        videos = context.getBean(StreamingVideoRepository.class);
        service = context.getBean(StreamingSessionVideoService.class);
        storage = context.getBean(StreamingMediaStoragePort.class);
        clock = context.getBean(MutableClock.class);
        when(storage.planStreamingVideo(anyString(), anyString())).thenAnswer(call -> target(call.getArgument(0), call.getArgument(1)));
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Object storage must not hold a DB transaction");
            VideoStorageTarget target = call.getArgument(1);
            objects.add(target.path());
            return new MediaStoragePort.StoredMedia(target.filename(), target.path());
        }).when(storage).storeStreamingVideo(anyString(), any(), any());
        seedKeypad("session", "trigger");
    }
    @AfterEach void close() throws Exception { if (schema != null) schema.close(); if (context != null) context.close(); }

    @Test void keypadStoresWithoutSecurityEventAndReplaysWithoutAnotherPut() {
        var first = service.upload("session", file(1));
        assertTrue(first.storedNow()); assertEquals(StreamingSessionVideo.State.STORED, first.video().uploadState());
        assertNull(first.video().securityEventId());
        assertFalse(service.upload("session", new MediaFile("renamed.mp4", "video/mp4", mp4(1))).storedNow());
        assertEquals(first.video(), service.find("session"));
        assertEquals(1, objects.size()); verify(storage).storeStreamingVideo(anyString(), any(), any());
        assertEquals(0, count("security_events")); assertEquals(0, count("vision_final_results"));
        assertEquals(0, count("streaming_agent_jobs")); assertEquals(0, count("vision_early_events"));
        assertEquals("REGISTERED", context.getBean(VisionSessionRepository.class).findBySessionId("session").orElseThrow().status().name());
    }
    @Test void audioKeepsExistingEvidenceAndEventState() {
        jdbc.update("INSERT INTO security_events VALUES ('event','PROCESSING',0.96,'original-path')");
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("audio", AUDIO, "event", null));
        when(context.getBean(VisionAudioTriggerPort.class).findIssuedTriggerId("event")).thenReturn(Optional.of("audio-trigger"));
        var video = service.upload("audio", file(1)).video();
        assertEquals("event", video.securityEventId()); assertEquals("audio-trigger", video.triggerId());
        assertEquals(1, count("security_events"));
        assertEquals("PROCESSING", jdbc.queryForObject("SELECT status FROM security_events", String.class));
        assertEquals(0.96, jdbc.queryForObject("SELECT audio_probability FROM security_events", Double.class));
        assertEquals("original-path", jdbc.queryForObject("SELECT video_path FROM security_events", String.class));
    }
    @Test void differentBytesConflictEvenWhenPreviousAttemptFailed() {
        doThrow(new IllegalStateException("storage unavailable")).when(storage).storeStreamingVideo(anyString(), any(), any());
        error("STREAMING_VIDEO_STORAGE_FAILED", () -> service.upload("session", file(1)));
        assertEquals(StreamingSessionVideo.State.FAILED, videos.find("session").orElseThrow().state());
        error("STREAMING_VIDEO_CONFLICT", () -> service.upload("session", file(2)));
        verify(storage).storeStreamingVideo(anyString(), any(), any());
    }
    @Test void concurrentUploadHasOneClaimAndOneObject() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
            VideoStorageTarget target = call.getArgument(1); objects.add(target.path());
            return new MediaStoragePort.StoredMedia(target.filename(), target.path());
        }).when(storage).storeStreamingVideo(anyString(), any(), any());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.upload("session", file(1)));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            error("STREAMING_VIDEO_IN_PROGRESS", () -> service.upload("session", file(1)));
            error("STREAMING_VIDEO_CONFLICT", () -> service.upload("session", file(2)));
            release.countDown(); assertTrue(first.get(10, TimeUnit.SECONDS).storedNow());
            assertFalse(service.upload("session", file(1)).storedNow());
            assertEquals(1, objects.size()); verify(storage).storeStreamingVideo(anyString(), any(), any());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void databaseFailureAfterPutRecoversSameObjectOnRetry() {
        var failing = mock(StreamingVideoRepository.class, org.mockito.AdditionalAnswers.delegatesTo(videos));
        doThrow(new DataAccessResourceFailureException("DB offline")).doAnswer(call -> videos.complete(call.getArgument(0)))
                .when(failing).complete(any());
        var worker = withRepository(failing);
        error("STREAMING_VIDEO_DATABASE_FAILED", () -> worker.upload("session", file(1)));
        assertEquals(StreamingSessionVideo.State.FAILED, videos.find("session").orElseThrow().state());
        assertEquals("DATABASE_FAILED", videos.find("session").orElseThrow().lastError());
        assertTrue(worker.upload("session", file(1)).storedNow());
        assertEquals(1, objects.size()); assertEquals(2, videos.find("session").orElseThrow().attempts());
    }
    @Test void databaseOutageLeavesRecoverableLeaseEvenWhenFailureCannotBeRecorded() {
        var failing = mock(StreamingVideoRepository.class, org.mockito.AdditionalAnswers.delegatesTo(videos));
        doThrow(new DataAccessResourceFailureException("DB offline")).when(failing).complete(any());
        doThrow(new DataAccessResourceFailureException("DB offline")).when(failing).failed(any(), anyString());
        error("STREAMING_VIDEO_DATABASE_FAILED", () -> withRepository(failing).upload("session", file(1)));
        assertEquals(StreamingSessionVideo.State.UPLOADING, videos.find("session").orElseThrow().state());
        error("STREAMING_VIDEO_IN_PROGRESS", () -> service.upload("session", file(1)));
        clock.advance(Duration.ofMinutes(6));
        assertTrue(service.upload("session", file(1)).storedNow());
        assertEquals(1, objects.size());
    }
    @Test void failedResponseAfterCommitReplaysWithoutAnotherStorageCall() {
        var failing = mock(StreamingVideoRepository.class, org.mockito.AdditionalAnswers.delegatesTo(videos));
        doThrow(new DataAccessResourceFailureException("DB read offline")).when(failing).linkedEventId(anyString());
        error("STREAMING_VIDEO_DATABASE_FAILED", () -> withRepository(failing).upload("session", file(1)));
        assertEquals(StreamingSessionVideo.State.STORED, videos.find("session").orElseThrow().state());
        assertFalse(service.upload("session", file(1)).storedNow());
        verify(storage).storeStreamingVideo(anyString(), any(), any());
    }
    @Test void restartAfterDbOutageRecoversExpiredLeaseAndFencesOldWorker() {
        var original = claim();
        clock.advance(Duration.ofMinutes(6));
        assertTrue(service.upload("session", file(1)).storedNow());
        error("STREAMING_VIDEO_IN_PROGRESS", () -> videos.complete(original));
        videos.failed(original, "OLD_WORKER");
        assertEquals(StreamingSessionVideo.State.STORED, videos.find("session").orElseThrow().state());
        assertEquals(2, videos.find("session").orElseThrow().attempts());
        assertEquals(1, objects.size());
    }
    @Test void storageFailureRetriesOriginalDestinationAndRejectsConfigDrift() {
        var original = claim(); videos.failed(original, "STORAGE_FAILED");
        when(storage.planStreamingVideo(anyString(), anyString())).thenAnswer(call -> {
            var target = target(call.getArgument(0), call.getArgument(1));
            return new VideoStorageTarget("S3", "other-bucket", target.filename(), "s3://other-bucket/" + target.filename());
        });
        error("STREAMING_VIDEO_STORAGE_CHANGED", () -> service.upload("session", file(1)));
        verify(storage, never()).storeStreamingVideo(anyString(), any(), any());
    }
    @Test void invalidSessionAndUnissuedAudioTriggerNeverReachStorage() {
        error("VISION_SESSION_NOT_FOUND", () -> service.upload("missing", file(1)));
        jdbc.update("INSERT INTO security_events (event_id) VALUES ('event')");
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("audio", AUDIO, "event", null));
        error("VISION_TRIGGER_NOT_FOUND", () -> service.upload("audio", file(1)));
        assertEquals(0, count("streaming_session_videos")); verifyNoInteractions(storage);
    }
    @Test void finalResultAndLaterKeypadEventAreLinkedRegardlessOfArrivalOrder() {
        service.upload("session", file(1));
        var polling = context.getBean(VisionPollingRepository.class);
        polling.enqueue(new VisionBinding("session", KEYPAD, "trigger", null), Duration.ofMinutes(5));
        var claim = polling.claim("session", Duration.ofSeconds(30)).orElseThrow();
        polling.finish(claim, result("session", "trigger", "KEYPAD", null), VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
        jdbc.update("INSERT INTO security_events (event_id) VALUES ('later-event')");
        jdbc.update("INSERT INTO streaming_agent_jobs (session_id,event_id,processing_state,next_attempt_at,created_at) VALUES ('session','later-event','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        assertEquals("later-event", service.find("session").securityEventId());
        assertNull(videos.find("session").orElseThrow().binding().securityEventId());
        assertFalse(service.upload("session", file(1)).storedNow());
        seedKeypad("final-first", "second-trigger");
        polling.enqueue(new VisionBinding("final-first", KEYPAD, "second-trigger", null), Duration.ofMinutes(5));
        polling.finish(polling.claim("final-first", Duration.ofSeconds(30)).orElseThrow(),
                result("final-first", "second-trigger", "KEYPAD", null), VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
        assertTrue(service.upload("final-first", file(2)).storedNow());
        assertEquals(2, count("vision_final_results")); assertEquals(1, count("security_events"));
    }
    @Test void bindingIsRevalidatedByRepositoryAndDbConstraintsProtectState() {
        var info = new Mp4VideoValidator().validate(file(1));
        error("STREAMING_VIDEO_BINDING_MISMATCH", () -> videos.reserve(new VisionBinding("session", KEYPAD, "other", null),
                info, target("session", info.sha256()), Duration.ofMinutes(5)));
        service.upload("session", file(1));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE streaming_session_videos SET upload_state='UPLOADING'"));
    }
    StreamingSessionVideo claim() {
        var info = new Mp4VideoValidator().validate(file(1));
        return videos.reserve(new VisionBinding("session", KEYPAD, "trigger", null), info, target("session", info.sha256()), Duration.ofMinutes(5));
    }
    StreamingSessionVideoService withRepository(StreamingVideoRepository repository) {
        return new StreamingSessionVideoService(context.getBean(VisionSessionRepository.class), context.getBean(KeypadTriggerPort.class),
                context.getBean(VisionAudioTriggerPort.class), new Mp4VideoValidator(), storage, repository,
                context.getBean(com.omniguardy.backend.domain.visionsession.application.port.in.LinkStreamingVideoInputPort.class), Duration.ofMinutes(5));
    }
    void seedKeypad(String session, String trigger) {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(
                tx -> context.getBean(KeypadTriggerPort.class).record(trigger, OffsetDateTime.now()));
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register(session, KEYPAD, null, trigger));
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    static VideoStorageTarget target(String session, String hash) { return new VideoStorageTarget("S3", "test-bucket", session + "-" + hash + ".mp4", "s3://test-bucket/" + session + "-" + hash + ".mp4"); }
    static void error(String code, Runnable action) { assertEquals(code, assertThrows(BusinessException.class, action::run).getErrorCode().getCode()); }
    @Configuration @EnableTransactionManagement
    @Import({JdbcVisionSessionRepository.class, JdbcKeypadTriggerAdapter.class, JdbcStreamingVideoRepository.class,
            JdbcVisionPollingRepository.class, StreamingSessionVideoService.class, Mp4VideoValidator.class})
    static class Config {
        @Bean com.omniguardy.backend.domain.visionsession.application.port.in.LinkStreamingVideoInputPort links() {
            return mock(com.omniguardy.backend.domain.visionsession.application.port.in.LinkStreamingVideoInputPort.class);
        }
        @Bean DataSource source() { return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager manager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean Duration streamingVideoUploadLease() { return Duration.ofMinutes(5); }
        @Bean VisionAudioTriggerPort audio() { return mock(VisionAudioTriggerPort.class); }
        @Bean StreamingMediaStoragePort storage() { return mock(StreamingMediaStoragePort.class); }
    }
}
