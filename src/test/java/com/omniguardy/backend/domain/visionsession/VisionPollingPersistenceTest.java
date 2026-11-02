package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.in.CompleteVisionSessionInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.*;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingVisionException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.persistence.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.streaming.StreamingVisionJsonMapper;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisionPollingPersistenceTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    CompleteVisionSessionInputPort completion;
    PollStreamingVisionService worker;
    VisionPollingRepository jobs;
    StreamingVisionQueryPort vision;
    MutableClock clock;
    java.sql.Connection schemaConnection;

    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE security_events (event_id VARCHAR(36) NOT NULL UNIQUE)");
        schemaConnection = context.getBean(DataSource.class).getConnection();
        for (String migration : List.of("V6__create_vision_sessions.sql", "V7__create_vision_early_events.sql",
                "V8__create_vision_polling_and_final_results.sql")) {
            var ddl = Files.readString(Path.of("src/main/resources/db/migration/" + migration))
                    .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "");
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(
                    ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8))).populate(schemaConnection);
        }
        completion = context.getBean(CompleteVisionSessionInputPort.class);
        worker = context.getBean(PollStreamingVisionService.class);
        jobs = context.getBean(VisionPollingRepository.class);
        vision = context.getBean(StreamingVisionQueryPort.class);
        clock = context.getBean(MutableClock.class);
        seedKeypad("session", "trigger");
        when(vision.status("session")).thenReturn(status("session", "trigger", "KEYPAD", null));
        when(vision.result("session")).thenReturn(Optional.of(result("session", "trigger", "KEYPAD", null)));
    }

    @AfterEach void close() throws Exception {
        if (schemaConnection != null) schemaConnection.close();
        if (context != null) context.close();
    }

    @Test void completeIsImmediateDurableAndSuccessfulEmptyEventsCommitOnce() {
        var first = completion.complete("session");
        verifyNoInteractions(vision);
        assertEquals(first, completion.complete("session"));
        assertEquals("REGISTERED", sessionState());
        worker.pollDue();
        assertEquals("COMPLETED", sessionState());
        assertEquals(VisionProcessingState.SUCCEEDED, jobs.find("session").orElseThrow().state());
        assertEquals(1, finalCount());
        assertEquals("[]", jdbc.queryForObject("SELECT vision_events_json FROM vision_final_results", String.class));
        assertEquals("[\"signal-001\"]", jdbc.queryForObject("SELECT related_early_event_ids_json FROM vision_final_results", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT tracked_person_count FROM vision_final_results", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT eligible_for_agent FROM vision_final_results", Boolean.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class));
        completion.complete("session"); worker.pollDue();
        verify(vision, times(1)).status("session"); verify(vision, times(1)).result("session");
        assertEquals(1, finalCount());
    }

    @Test void concurrentCompletionNotificationsAndClaimsProduceOneJobAndOneLease() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var barrier = new CyclicBarrier(8);
            var tasks = new ArrayList<Callable<VisionProcessingView>>();
            for (int i = 0; i < 8; i++) tasks.add(() -> { barrier.await(10, TimeUnit.SECONDS); return completion.complete("session"); });
            var futures = pool.invokeAll(tasks, 20, TimeUnit.SECONDS);
            var first = futures.getFirst().get();
            for (var future : futures) assertEquals(first, future.get());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_polling_jobs", Integer.class));
            var claimTasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 8; i++) claimTasks.add(() -> jobs.claim("session", Duration.ofSeconds(30)).isPresent());
            int claimed = 0;
            for (var future : pool.invokeAll(claimTasks, 20, TimeUnit.SECONDS)) if (future.get()) claimed++;
            assertEquals(1, claimed);
        } finally { pool.shutdownNow(); }
    }

    @Test void processingAndResult202AreRescheduledWithoutBusyLoop() {
        var pending = new StreamingVisionJsonMapper().status(statusJson("session", "trigger", "KEYPAD", null)
                .replace("COMPLETED", "PROCESSING").replace("COMPLETE_KEYPAD", "PROCESSING")
                .replace("\"finalResultAvailable\":true", "\"finalResultAvailable\":false"));
        when(vision.status("session")).thenReturn(pending, status("session", "trigger", "KEYPAD", null));
        completion.complete("session"); worker.pollDue(); worker.pollDue();
        verify(vision, never()).result(any());
        assertEquals("ANALYSIS_PENDING", jobs.find("session").orElseThrow().reason());
        clock.advance(Duration.ofSeconds(2));
        when(vision.result("session")).thenReturn(Optional.empty(), Optional.of(result("session", "trigger", "KEYPAD", null)));
        worker.pollDue();
        assertEquals("RESULT_PENDING", jobs.find("session").orElseThrow().reason());
        assertEquals(0, finalCount());
        clock.advance(Duration.ofSeconds(2)); worker.pollDue();
        assertEquals(1, finalCount()); assertEquals("COMPLETED", sessionState());
    }

    @Test void receivingAndCompletedWithoutAvailableResultRemainPending() {
        var mapper = new StreamingVisionJsonMapper();
        when(vision.status("session")).thenReturn(mapper.status(statusJson("session", "trigger", "KEYPAD", null)
                .replace("COMPLETED", "RECEIVING").replace("\"inputComplete\":true", "\"inputComplete\":false")),
                mapper.status(statusJson("session", "trigger", "KEYPAD", null).replace("\"finalResultAvailable\":true", "\"finalResultAvailable\":false")));
        completion.complete("session"); worker.pollDue(); clock.advance(Duration.ofSeconds(2)); worker.pollDue();
        verify(vision, never()).result(any()); assertEquals("REGISTERED", sessionState());
    }

    @Test void audioRequiresItsOwnCompletenessAndBothIds() {
        jdbc.update("INSERT INTO security_events VALUES ('event')");
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("audio", AUDIO, "event", null));
        when(context.getBean(VisionAudioTriggerPort.class).findIssuedTriggerId("event")).thenReturn(Optional.of("audio-trigger"));
        when(vision.status("audio")).thenReturn(status("audio", "audio-trigger", "AUDIO", "event"));
        when(vision.result("audio")).thenReturn(Optional.of(result("audio", "audio-trigger", "AUDIO", "event")));
        completion.complete("audio"); worker.pollDue();
        assertEquals("COMPLETED", context.getBean(VisionSessionRepository.class).findBySessionId("audio").orElseThrow().status().name());
        assertEquals("VALID", jdbc.queryForObject("SELECT outcome FROM vision_final_results", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
    }

    @Test void wrongTriggerCompletenessIsRejectedAndNeverEligible() {
        when(vision.result("session")).thenReturn(Optional.of(new StreamingVisionJsonMapper().result(
                resultJson("session", "trigger", "KEYPAD", null).replace("COMPLETE_KEYPAD", "COMPLETE_AUDIO"))));
        completion.complete("session"); worker.pollDue(); assertFallback("INVALID_FINAL_RESULT", "REJECTED");
    }

    @Test void partialPreservesNullableCountAndHeterogeneousEventsWithoutSideEffects() {
        var json = resultJson("session", "trigger", "KEYPAD", null).replace("\"status\":\"success\"", "\"status\":\"partial\"")
                .replace("COMPLETE_KEYPAD", "PARTIAL_SAMPLED_TIME_AXIS").replace("\"trackedPersonCount\":0", "\"trackedPersonCount\":null")
                .replace("\"visionEvents\":[]", "\"visionEvents\":[{\"eventType\":\"NEW_EVENT\",\"details\":{\"nested\":[1,true,null]}}]");
        when(vision.result("session")).thenReturn(Optional.of(new StreamingVisionJsonMapper().result(json)));
        completion.complete("session"); worker.pollDue(); assertFallback("PARTIAL_RESULT", "PARTIAL");
        assertNull(jdbc.queryForObject("SELECT tracked_person_count FROM vision_final_results", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT raw_result_json FROM vision_final_results", String.class).contains("NEW_EVENT"));
    }

    @Test void failedResultIsStoredIncludingError() {
        var mapper = new StreamingVisionJsonMapper();
        when(vision.status("session")).thenReturn(mapper.status(statusJson("session", "trigger", "KEYPAD", null)
                .replace("COMPLETED", "FAILED").replace("COMPLETE_KEYPAD", "FAILED")));
        when(vision.result("session")).thenReturn(Optional.of(mapper.result(resultJson("session", "trigger", "KEYPAD", null)
                .replace("COMPLETED", "FAILED").replace("COMPLETE_KEYPAD", "FAILED").replace("\"success\"", "\"failed\"")
                .replace("\"error\":null", "\"error\":\"worker failed\""))));
        completion.complete("session"); worker.pollDue(); assertFallback("VISION_FAILED", "FAILED");
        assertEquals("worker failed", jobs.find("session").orElseThrow().lastError());
    }

    @Test void failedStatusWithoutResultStopsPolling() {
        when(vision.status("session")).thenReturn(new StreamingVisionJsonMapper().status(statusJson("session", "trigger", "KEYPAD", null)
                .replace("COMPLETED", "FAILED").replace("\"finalResultAvailable\":true", "\"finalResultAvailable\":false")));
        completion.complete("session"); worker.pollDue();
        assertEquals("VISION_FAILED", jobs.find("session").orElseThrow().reason());
        assertEquals("FAILED", sessionState()); assertEquals(0, finalCount()); verify(vision, never()).result(any());
    }

    @Test void finalSessionMismatchIsQuarantinedUnderRequestedSession() {
        when(vision.result("session")).thenReturn(Optional.of(result("different", "trigger", "KEYPAD", null)));
        completion.complete("session"); worker.pollDue(); assertFallback("BINDING_MISMATCH", "REJECTED");
        assertEquals("session", jdbc.queryForObject("SELECT session_id FROM vision_final_results", String.class));
    }

    @Test void statusTriggerMismatchStopsBeforeResultFetch() {
        when(vision.status("session")).thenReturn(status("session", "wrong-trigger", "KEYPAD", null));
        completion.complete("session"); worker.pollDue();
        assertEquals("BINDING_MISMATCH", jobs.find("session").orElseThrow().reason());
        assertEquals(0, finalCount()); verify(vision, never()).result(any());
    }

    @Test void networkFailuresRetryThenPersistTimeoutWithoutExtendingOriginalDeadline() {
        when(vision.status("session")).thenThrow(new StreamingVisionException("NETWORK_ERROR", true));
        var original = completion.complete("session"); worker.pollDue();
        assertEquals("NETWORK_ERROR", jobs.find("session").orElseThrow().reason());
        clock.advance(Duration.ofSeconds(2));
        assertEquals(original.deadlineAt(), completion.complete("session").deadlineAt()); worker.pollDue();
        clock.advance(Duration.ofMinutes(5)); worker.pollDue();
        assertEquals("TIMEOUT", jobs.find("session").orElseThrow().reason());
        assertEquals("FAILED", sessionState()); verify(vision, times(2)).status("session");
    }

    @Test void upstreamNotFoundAndInvalidJsonDoNotRetry() {
        when(vision.result("session")).thenThrow(new StreamingVisionException("UPSTREAM_NOT_FOUND", false));
        completion.complete("session"); worker.pollDue();
        assertEquals("UPSTREAM_NOT_FOUND", jobs.find("session").orElseThrow().reason());
        assertEquals("FAILED", sessionState());
    }

    @Test void restartReclaimsExpiredLeaseAndOldWorkerCannotOverwriteOrReschedule() {
        completion.complete("session");
        var old = jobs.claim("session", Duration.ofSeconds(30)).orElseThrow();
        clock.advance(Duration.ofSeconds(31));
        var newer = jobs.claim("session", Duration.ofSeconds(30)).orElseThrow();
        var result = result("session", "trigger", "KEYPAD", null);
        jobs.finish(old, result, VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
        jobs.reschedule(old, Duration.ofSeconds(2), "STALE", null, null);
        assertEquals(0, finalCount()); assertEquals(newer.leaseToken(), jobs.find("session").orElseThrow().leaseToken());
        jobs.finish(newer, result, VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
        jobs.finish(old, result, VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
        assertEquals(1, finalCount()); assertEquals("COMPLETED", sessionState());
    }

    @Test void finalInsertAndBothStateChangesRollBackTogetherOnDatabaseFailure() {
        completion.complete("session");
        var claim = jobs.claim("session", Duration.ofSeconds(30)).orElseThrow();
        jdbc.execute("ALTER TABLE vision_polling_jobs ADD CONSTRAINT test_reject_success CHECK (processing_state <> 'SUCCEEDED')");
        assertThrows(DataIntegrityViolationException.class, () -> jobs.finish(claim, result("session", "trigger", "KEYPAD", null),
                VisionFinalOutcome.VALID, "VALID_RESULT", null, null));
        assertEquals(0, finalCount()); assertEquals("REGISTERED", sessionState());
        assertEquals(VisionProcessingState.RUNNING, jobs.find("session").orElseThrow().state());
        jdbc.execute("ALTER TABLE vision_polling_jobs DROP CONSTRAINT test_reject_success");
        jobs.finish(claim, result("session", "trigger", "KEYPAD", null), VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
        assertEquals(1, finalCount());
    }

    @Test void lateResultNeverBecomesEligibleEvenIfNetworkReturnedSuccessfully() {
        completion.complete("session");
        var claim = jobs.claim("session", Duration.ofMinutes(10)).orElseThrow();
        clock.advance(Duration.ofMinutes(6));
        jobs.finish(claim, result("session", "trigger", "KEYPAD", null), VisionFinalOutcome.VALID, "VALID_RESULT", null, null);
        assertEquals(0, finalCount()); assertEquals("FAILED", sessionState());
        assertEquals("TIMEOUT", jobs.find("session").orElseThrow().reason());
    }

    @Test void invalidAndMissingSessionDoNotCreateJobs() {
        assertThrows(BusinessException.class, () -> completion.complete("../bad"));
        assertEquals("VISION_SESSION_NOT_FOUND", assertThrows(BusinessException.class, () -> completion.complete("missing")).getErrorCode().getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM vision_polling_jobs", Integer.class));
    }

    void seedKeypad(String session, String trigger) {
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(
                tx -> context.getBean(KeypadTriggerPort.class).record(trigger, OffsetDateTime.now()));
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register(session, KEYPAD, null, trigger));
    }
    String sessionState() { return context.getBean(VisionSessionRepository.class).findBySessionId("session").orElseThrow().status().name(); }
    int finalCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM vision_final_results", Integer.class); }
    void assertFallback(String reason, String outcome) {
        assertEquals("FAILED", sessionState());
        assertEquals(VisionProcessingState.FALLBACK_REQUIRED, jobs.find("session").orElseThrow().state());
        assertEquals(reason, jobs.find("session").orElseThrow().reason());
        assertEquals(outcome, jdbc.queryForObject("SELECT outcome FROM vision_final_results", String.class));
        assertFalse(jdbc.queryForObject("SELECT eligible_for_agent FROM vision_final_results", Boolean.class));
    }

    @Configuration @EnableTransactionManagement
    @Import({JdbcVisionSessionRepository.class, JdbcKeypadTriggerAdapter.class, JdbcVisionPollingRepository.class,
            CompleteVisionSessionService.class, PollStreamingVisionService.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager manager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean VisionPollingPolicy policy() { return new VisionPollingPolicy(Duration.ofSeconds(2), Duration.ofMinutes(5), Duration.ofSeconds(30), 10); }
        @Bean VisionAudioTriggerPort audio() { return mock(VisionAudioTriggerPort.class); }
        @Bean StreamingVisionQueryPort vision() { return mock(StreamingVisionQueryPort.class); }
    }
}
