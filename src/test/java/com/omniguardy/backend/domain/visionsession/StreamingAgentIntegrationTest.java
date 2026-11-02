package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.BackendApplication;
import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.securityevent.application.port.out.RiskAssessmentPort;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.ProcessStreamingAgentService;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.agent.StreamingAgentVisionMapper;
import com.omniguardy.backend.domain.visionsession.infrastructure.streaming.StreamingVisionJsonMapper;
import com.omniguardy.backend.global.config.FirebaseConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = BackendApplication.class, properties = {
        "spring.profiles.active=prod", "DB_URL=jdbc:h2:mem:streaming-agent;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa", "DB_PASSWORD=synthetic-test-only", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "JWT_SECRET_KEY=streaming-agent-test-secret-at-least-32-bytes", "AUDIO_DEVICE_API_KEY=test-key",
        "MQTT_BROKER_URL=tcp://127.0.0.1:1", "MQTT_USERNAME=backend", "MQTT_PASSWORD=test-only",
        "OMNIGUARDY_DEMO_USERID=1", "AGENT_AI_ENABLED=false", "ai.fastapi.streaming-vision.polling-enabled=false",
        "omniguardy.streaming-agent.enabled=false", "AI_FASTAPI_AUDIO_BASE_URL=https://audio.invalid",
        "AI_FASTAPI_VISION_BASE_URL=https://vision.invalid", "FIREBASE_CREDENTIALS_PATH=/mocked-not-used.json",
        "AWS_REGION=ap-northeast-2", "S3_BUCKET_NAME=test-only"
})
class StreamingAgentIntegrationTest {
    @MockitoBean FirebaseConfig firebase;
    @MockitoBean RiskAssessmentPort risk;
    @MockitoBean NotificationService notifications;
    @MockitoBean PlaySoundUseCase sound;
    @MockitoBean DemoUserResolver owner;
    @MockitoBean(name = "visionStreamingClock") Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecurityEventRepository securityEvents;
    @Autowired UserRepository users;
    @Autowired VisionSessionRepository sessions;
    @Autowired VisionPollingRepository polling;
    @Autowired KeypadTriggerPort keypad;
    @Autowired StreamingAgentRepository jobs;
    @Autowired StreamingAgentEventPort eventPort;
    @Autowired ProcessStreamingAgentService worker;
    @Autowired PlatformTransactionManager transactions;
    MutableClock time;
    User user;
    java.sql.Connection schema;

    @BeforeAll void migrations() throws Exception {
        schema = Objects.requireNonNull(jdbc.getDataSource()).getConnection();
        for (String file : List.of("V6__create_vision_sessions.sql", "V7__create_vision_early_events.sql",
                "V8__create_vision_polling_and_final_results.sql", "V9__create_streaming_agent_jobs.sql")) {
            var ddl = Files.readString(Path.of("src/main/resources/db/migration/" + file))
                    .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "");
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(
                    ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8))).populate(schema);
        }
    }
    @AfterAll void close() throws Exception { schema.close(); }
    @BeforeEach void setup() {
        for (String table : List.of("streaming_agent_jobs", "vision_final_results", "vision_polling_jobs", "vision_early_events",
                "vision_sessions", "keypad_camera_triggers", "security_events", "users")) jdbc.update("DELETE FROM " + table);
        time = new MutableClock(); when(clock.instant()).thenAnswer(call -> time.instant());
        user = users.save(User.builder().email("streaming@test.invalid").name("test").password("synthetic").phoneNumber("010-test").build());
        when(owner.resolve()).thenReturn(Optional.of(user));
        when(risk.assess(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Agent call must not hold a DB transaction");
            return new AgentAssessment(RiskLevel.LOW, 42, "기존 입력의 감지 이벤트를 확인했습니다.");
        });
    }

    @Test void validKeypadReusesExistingAssessmentAndResponseExactlyOnce() {
        seedKeypad("session", "SILENT_SIGNAL", 0);
        worker.processDue(); worker.processDue();
        var job = jobs.find("session").orElseThrow();
        assertEquals(StreamingAgentJob.State.SUCCEEDED, job.state()); assertEquals(1, job.agentAttempts());
        assertEquals(1, countEvents());
        var event = securityEvents.findByEventId(job.eventId()).orElseThrow();
        assertEquals("HIGH", event.getRiskLevel()); assertEquals(42, event.getRiskScore());
        assertNull(event.getVisionPrediction()); assertNull(event.getVisionConfidence()); assertNull(event.getClassProbabilities());
        assertNull(event.getVideoPath()); assertEquals(0, event.getPersonCount());
        var input = org.mockito.ArgumentCaptor.forClass(AgentContext.class); verify(risk).assess(input.capture());
        assertEquals("exhibition_rules", input.getValue().vision().analysisMode());
        assertEquals(0, input.getValue().vision().personCount()); assertNull(input.getValue().vision().confidence());
        assertTrue(input.getValue().vision().classProbabilities().isEmpty());
        verify(notifications).sendSecurityAlert(any(), eq(3), eq("KEYPAD"), eq(job.eventId()));
        verify(sound).play(PlaySoundType.WARNING);
    }
    @Test void validAudioUsesExistingEventAndPreservesAudioEvidence() {
        seedAudio("audio", "CAMERA_OCCLUSION_SUSPECTED"); worker.processDue();
        var event = securityEvents.findByEventId("audio-event").orElseThrow();
        assertEquals(1, countEvents()); assertEquals("MEDIUM", event.getRiskLevel());
        assertEquals(AudioEventType.KNOCK, event.getAudioEventType()); assertEquals(0.95, event.getAudioProbability());
        assertEquals("audio-event", jobs.find("audio").orElseThrow().eventId());
        verify(risk).assess(any()); verify(notifications).sendSecurityAlert(any(), eq(2), eq("AUDIO"), eq("audio-event"));
        verifyNoInteractions(sound);
    }
    @Test void audioDwellKeepsExistingLowRiskRule() {
        seedAudio("audio", "PERSON_DWELL_OBSERVED"); worker.processDue();
        assertEquals("LOW", securityEvents.findByEventId("audio-event").orElseThrow().getRiskLevel());
        verify(risk).assess(any()); verifyNoInteractions(notifications, sound);
    }
    @Test void keypadWithNoEventsCreatesNoSecurityEventOrAgentCall() {
        seedKeypad("session", null, 0); worker.processDue();
        assertEquals(0, countEvents()); assertEquals("NO_EVENTS", jobs.find("session").orElseThrow().reason());
        verifyNoInteractions(risk, notifications, sound);
    }
    @Test void audioWithNoEventsCompletesExistingEventWithoutRiskAssessment() {
        seedAudio("audio", null); worker.processDue();
        assertEquals(1, countEvents()); assertEquals("COMPLETED", securityEvents.findByEventId("audio-event").orElseThrow().getStatus());
        assertEquals("NO_RISK", jobs.find("audio").orElseThrow().reason()); verifyNoInteractions(risk, notifications, sound);
    }
    @Test void nullCountIsBlockedForFutureFallbackAndNeverRetried() {
        seedKeypad("session", "SILENT_SIGNAL", null); worker.processDue();
        var first = jobs.find("session").orElseThrow();
        assertEquals(StreamingAgentJob.State.BLOCKED, first.state()); assertEquals("UNKNOWN_PERSON_COUNT", first.reason());
        assertTrue(first.fallbackRequired()); assertEquals(0, first.agentAttempts()); assertEquals(0, countEvents());
        time.advance(Duration.ofHours(1)); worker.processDue();
        assertEquals(first.attempts(), jobs.find("session").orElseThrow().attempts());
        assertEquals("VALID", jdbc.queryForObject("SELECT outcome FROM vision_final_results", String.class));
        verifyNoInteractions(risk, notifications, sound);
    }
    @Test void partialFailedAndRejectedResultsNeverCreateAgentJobs() {
        for (var outcome : new VisionFinalOutcome[]{VisionFinalOutcome.PARTIAL, VisionFinalOutcome.FAILED, VisionFinalOutcome.REJECTED}) {
            seed("invalid-" + outcome, VisionTriggerType.KEYPAD, "SILENT_SIGNAL", 0, outcome);
        }
        worker.processDue(); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM streaming_agent_jobs", Integer.class));
        assertEquals(0, countEvents()); verifyNoInteractions(risk, notifications, sound);
    }
    @Test void unknownEventIsBlockedWithoutInventingRiskRules() {
        seedKeypad("session", "FUTURE_EVENT", 1); worker.processDue();
        assertEquals("UNSUPPORTED_EVENT", jobs.find("session").orElseThrow().reason()); assertEquals(0, countEvents());
        verifyNoInteractions(risk, notifications, sound);
    }
    @Test void changedRawBindingIsRevalidatedDespiteValidDbFlags() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        var raw = jdbc.queryForObject("SELECT raw_result_json FROM vision_final_results", String.class).replace("\"session\"", "\"other\"");
        jdbc.update("UPDATE vision_final_results SET raw_result_json = ?", raw); worker.processDue();
        assertEquals(StreamingAgentJob.State.BLOCKED, jobs.find("session").orElseThrow().state());
        assertEquals(0, countEvents()); verifyNoInteractions(risk, notifications, sound);
    }
    @Test void concurrentWorkersDoNotDuplicateAgentOrResponses() throws Exception {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(risk.assess(any())).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
            return new AgentAssessment(RiskLevel.LOW, 45, "actual evidence");
        });
        var pool = Executors.newFixedThreadPool(3);
        try {
            var first = pool.submit(worker::processDue); assertTrue(entered.await(10, TimeUnit.SECONDS));
            var second = pool.submit(worker::processDue); var third = pool.submit(worker::processDue);
            second.get(10, TimeUnit.SECONDS); third.get(10, TimeUnit.SECONDS);
            release.countDown(); first.get(10, TimeUnit.SECONDS);
            assertEquals(1, countEvents()); verify(risk).assess(any()); verify(sound).play(PlaySoundType.WARNING);
            assertEquals(StreamingAgentJob.State.SUCCEEDED, jobs.find("session").orElseThrow().state());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void restartAfterPreparedEventReusesItsIdentity() {
        seedKeypad("session", "SILENT_SIGNAL", 1); jobs.enqueue("session");
        var claim = jobs.claim("session", Duration.ofSeconds(30)).orElseThrow();
        var prepared = eventPort.prepare(claim, new StreamingAgentVisionMapper().map(jobs.source("session").orElseThrow()));
        assertEquals(1, countEvents()); time.advance(Duration.ofSeconds(31)); worker.processDue();
        assertEquals(prepared.eventId(), jobs.find("session").orElseThrow().eventId());
        assertEquals(1, countEvents()); verify(risk).assess(any());
    }
    @Test void interruptedCallingIsUncertainAndNeverAutomaticallyReinvoked() {
        var calling = prepareCalling(); time.advance(Duration.ofMinutes(6)); worker.processDue(); worker.processDue();
        var job = jobs.find("session").orElseThrow(); assertEquals(StreamingAgentJob.State.UNCERTAIN, job.state());
        assertEquals("UNKNOWN_NOT_REPLAYED", job.responseState()); assertEquals(calling.agentAttempts(), job.agentAttempts());
        verifyNoInteractions(risk, notifications, sound);
    }
    @Test void restartAfterAssessmentCommitDoesNotReplayResponse() {
        var calling = prepareCalling(); eventPort.analyze(calling);
        time.advance(Duration.ofMinutes(6)); worker.processDue();
        assertEquals(StreamingAgentJob.State.SUCCEEDED, jobs.find("session").orElseThrow().state());
        assertEquals("UNKNOWN_NOT_REPLAYED", jobs.find("session").orElseThrow().responseState());
        verify(risk).assess(any()); verify(sound).play(PlaySoundType.WARNING);
        verify(notifications).sendSecurityAlert(any(), eq(3), eq("KEYPAD"), eq(calling.eventId()));
    }
    @Test void lateLiveCallerCanResolveItsUncertainAttemptWithoutSecondInvocation() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        when(risk.assess(any())).thenAnswer(call -> {
            time.advance(Duration.ofMinutes(6)); worker.processDue();
            return new AgentAssessment(RiskLevel.LOW, 40, "live result");
        });
        worker.processDue(); assertEquals(StreamingAgentJob.State.SUCCEEDED, jobs.find("session").orElseThrow().state());
        verify(risk).assess(any()); verify(sound).play(PlaySoundType.WARNING);
    }
    @Test void knownNetworkFailureRetriesWithoutDuplicatingResponses() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        when(risk.assess(any())).thenThrow(new RuntimeException(new java.net.SocketTimeoutException("test")))
                .thenReturn(new AgentAssessment(RiskLevel.LOW, 30, "retry evidence"));
        worker.processDue(); assertEquals(StreamingAgentJob.State.PENDING, jobs.find("session").orElseThrow().state());
        verifyNoInteractions(notifications, sound); time.advance(Duration.ofSeconds(10)); worker.processDue();
        assertEquals(StreamingAgentJob.State.SUCCEEDED, jobs.find("session").orElseThrow().state());
        assertEquals(1, countEvents()); verify(risk, times(2)).assess(any()); verify(sound).play(PlaySoundType.WARNING);
    }
    @Test void repeatedNetworkFailuresExhaustFiniteRetryBudget() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        when(risk.assess(any())).thenThrow(new RuntimeException(new java.net.SocketTimeoutException("test")));
        for (int i = 0; i < 5; i++) { worker.processDue(); time.advance(Duration.ofSeconds(10)); }
        assertEquals(StreamingAgentJob.State.FAILED, jobs.find("session").orElseThrow().state());
        assertEquals(3, jobs.find("session").orElseThrow().agentAttempts()); verify(risk, times(3)).assess(any());
        verifyNoInteractions(notifications, sound);
    }
    @Test void stalePreparationLeaseCannotCreateAnEvent() {
        seedKeypad("session", "SILENT_SIGNAL", 1); jobs.enqueue("session");
        var old = jobs.claim("session", Duration.ofSeconds(30)).orElseThrow(); time.advance(Duration.ofSeconds(31));
        jobs.claim("session", Duration.ofSeconds(30)).orElseThrow();
        var prepared = eventPort.prepare(old, new StreamingAgentVisionMapper().map(jobs.source("session").orElseThrow()));
        assertEquals(StreamingAgentEventPort.Action.STALE, prepared.action()); assertEquals(0, countEvents());
    }
    @Test void eventCreationAndLedgerLinkRollbackTogether() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        jdbc.execute("ALTER TABLE streaming_agent_jobs ADD CONSTRAINT test_no_link CHECK (event_id IS NULL)");
        try {
            worker.processDue(); assertEquals(0, countEvents());
            assertNull(jobs.find("session").orElseThrow().eventId());
        } finally { jdbc.execute("ALTER TABLE streaming_agent_jobs DROP CONSTRAINT test_no_link"); }
        time.advance(Duration.ofSeconds(10)); worker.processDue(); assertEquals(1, countEvents());
        verify(risk).assess(any());
    }

    @Test void nullAudioCountRequiresFutureFallbackWithoutChangingOriginalEvent() {
        seed("audio", VisionTriggerType.AUDIO, "PERSON_DWELL_OBSERVED", null, VisionFinalOutcome.VALID);
        worker.processDue(); time.advance(Duration.ofHours(1)); worker.processDue();
        assertTrue(jobs.find("audio").orElseThrow().fallbackRequired());
        assertEquals("UNKNOWN_PERSON_COUNT", jobs.find("audio").orElseThrow().reason());
        assertEquals("CAMERA_REQUESTED", securityEvents.findByEventId("audio-event").orElseThrow().getStatus());
        verifyNoInteractions(risk, notifications, sound);
    }
    @Test void permanentAgentFailureIsRecordedWithoutAutomaticRetryOrResponses() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        when(risk.assess(any())).thenThrow(new com.omniguardy.backend.global.error.exception.BusinessException(
                com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID));
        worker.processDue(); time.advance(Duration.ofMinutes(1)); worker.processDue();
        assertEquals(StreamingAgentJob.State.FAILED, jobs.find("session").orElseThrow().state());
        verify(risk).assess(any()); verifyNoInteractions(notifications, sound);
    }
    @Test void existingBestEffortDeliveryFailureDoesNotReplayWholeAssessment() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        doThrow(new IllegalStateException("delivery failed")).when(notifications).sendSecurityAlert(any(), anyInt(), anyString(), anyString());
        worker.processDue(); worker.processDue();
        assertEquals(StreamingAgentJob.State.SUCCEEDED, jobs.find("session").orElseThrow().state());
        assertEquals("BEST_EFFORT_ATTEMPTED", jobs.find("session").orElseThrow().responseState());
        verify(risk).assess(any()); verify(notifications).sendSecurityAlert(any(), anyInt(), anyString(), anyString());
        verify(sound).play(PlaySoundType.WARNING);
    }
    @Test void failedLedgerCommitAfterAgentSuccessRecoversWithoutRepeatingEffects() {
        seedKeypad("session", "SILENT_SIGNAL", 1);
        jdbc.execute("ALTER TABLE streaming_agent_jobs ADD CONSTRAINT test_no_success CHECK (processing_state <> 'SUCCEEDED')");
        try { worker.processDue(); }
        finally { jdbc.execute("ALTER TABLE streaming_agent_jobs DROP CONSTRAINT test_no_success"); }
        assertEquals(StreamingAgentJob.State.CALLING, jobs.find("session").orElseThrow().state());
        time.advance(Duration.ofMinutes(6)); worker.processDue();
        assertEquals(StreamingAgentJob.State.SUCCEEDED, jobs.find("session").orElseThrow().state());
        verify(risk).assess(any()); verify(sound).play(PlaySoundType.WARNING);
        verify(notifications).sendSecurityAlert(any(), anyInt(), anyString(), anyString());
    }
    @Test void alreadyCompletedLegacyEventIsNotOverwrittenOrAssessedAgain() {
        seedAudio("audio", "CAMERA_OCCLUSION_SUSPECTED");
        var event = securityEvents.findByEventId("audio-event").orElseThrow();
        event.updateAgentResult("MEDIUM", 72, "legacy assessment"); securityEvents.save(event);
        worker.processDue();
        assertEquals("ALREADY_COMPLETED", jobs.find("audio").orElseThrow().reason());
        assertEquals(72, securityEvents.findByEventId("audio-event").orElseThrow().getRiskScore());
        verifyNoInteractions(risk, notifications, sound);
    }

    StreamingAgentJob prepareCalling() {
        seedKeypad("session", "SILENT_SIGNAL", 1); jobs.enqueue("session");
        var claim = jobs.claim("session", Duration.ofSeconds(30)).orElseThrow();
        eventPort.prepare(claim, new StreamingAgentVisionMapper().map(jobs.source("session").orElseThrow()));
        assertTrue(jobs.startCalling(jobs.find("session").orElseThrow(), Duration.ofMinutes(5)));
        return jobs.find("session").orElseThrow();
    }
    void seedKeypad(String session, String type, Integer count) { seed(session, VisionTriggerType.KEYPAD, type, count, VisionFinalOutcome.VALID); }
    void seedAudio(String session, String type) { seed(session, VisionTriggerType.AUDIO, type, 1, VisionFinalOutcome.VALID); }
    void seed(String session, VisionTriggerType triggerType, String eventType, Integer count, VisionFinalOutcome outcome) {
        String trigger = "trigger-" + session; String securityId = null;
        if (triggerType == VisionTriggerType.KEYPAD) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> keypad.record(trigger, OffsetDateTime.ofInstant(time.instant(), ZoneOffset.UTC)));
        } else {
            securityId = session + "-event";
            securityEvents.save(SecurityEvent.builder().eventId(securityId).triggerId(trigger).triggerType(EventTriggerType.AUDIO)
                    .triggeredAt(OffsetDateTime.ofInstant(time.instant(), ZoneOffset.UTC)).status("CAMERA_REQUESTED")
                    .audioEventType(AudioEventType.KNOCK).audioProbability(0.95).user(user).build());
        }
        sessions.insert(VisionSession.register(session, triggerType, securityId, triggerType == VisionTriggerType.KEYPAD ? trigger : null));
        var binding = new VisionBinding(session, triggerType, trigger, securityId); polling.enqueue(binding, Duration.ofMinutes(5));
        var claim = polling.claim(session, Duration.ofSeconds(30)).orElseThrow();
        var events = eventType == null ? "[]" : "[{\"eventType\":\"" + eventType + "\",\"detectedFrame\":114,\"confirmedFrame\":114,\"capturedAt\":\"3.8\"}]";
        var json = resultJson(session, trigger, triggerType.name(), securityId).replace("\"visionEvents\":[]", "\"visionEvents\":" + events)
                .replace("\"trackedPersonCount\":0", "\"trackedPersonCount\":" + (count == null ? "null" : count));
        if (outcome == VisionFinalOutcome.PARTIAL) json = json.replace("COMPLETE_KEYPAD", "PARTIAL_SAMPLED_TIME_AXIS").replace("\"success\"", "\"partial\"");
        if (outcome == VisionFinalOutcome.FAILED) json = json.replace("COMPLETED", "FAILED").replace("\"success\"", "\"failed\"");
        polling.finish(claim, new StreamingVisionJsonMapper().result(json), outcome, outcome.name(), null, null);
    }
    int countEvents() { return jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class); }
}
