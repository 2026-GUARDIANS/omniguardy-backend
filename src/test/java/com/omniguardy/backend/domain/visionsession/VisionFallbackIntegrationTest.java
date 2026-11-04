package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.BackendApplication;
import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.securityevent.application.port.out.*;
import com.omniguardy.backend.domain.securityevent.application.usecase.AnalyzeAgentRiskUseCase;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.in.StreamingSessionVideoInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.*;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.config.FirebaseConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import software.amazon.awssdk.core.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes=BackendApplication.class,properties={
        "spring.profiles.active=prod", "DB_URL=jdbc:h2:mem:vision-fallback;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "DB_USERNAME=sa","DB_PASSWORD=synthetic-test-only","spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop",
        "JWT_SECRET_KEY=fallback-test-secret-at-least-32-bytes","AUDIO_DEVICE_API_KEY=test-key",
        "MQTT_BROKER_URL=tcp://127.0.0.1:1","MQTT_USERNAME=backend","MQTT_PASSWORD=test-only",
        "OMNIGUARDY_DEMO_USERID=1","AGENT_AI_ENABLED=false","ai.fastapi.streaming-vision.polling-enabled=false",
        "omniguardy.streaming-agent.enabled=false","omniguardy.vision-fallback.enabled=false","omniguardy.streaming-video.event-link-enabled=false",
        "omniguardy.vision-fallback.retry-delay=1s","omniguardy.vision-fallback.video-wait=10s",
        "AI_FASTAPI_AUDIO_BASE_URL=https://audio.invalid","AI_FASTAPI_VISION_BASE_URL=https://vision.invalid",
        "FIREBASE_CREDENTIALS_PATH=/mocked-not-used.json","AWS_REGION=ap-northeast-2","S3_BUCKET_NAME=test-bucket"
})
class VisionFallbackIntegrationTest {
    @MockitoBean FirebaseConfig firebase;
    @MockitoBean RiskAssessmentPort risk;
    @MockitoBean NotificationService notifications;
    @MockitoBean PlaySoundUseCase sound;
    @MockitoBean DemoUserResolver owner;
    @MockitoBean S3Client s3;
    @MockitoBean VisionAnalysisPort vision;
    @MockitoBean StreamingVisionQueryPort streaming;
    @MockitoBean(name="visionStreamingClock") Clock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired SecurityEventRepository securityEvents;
    @Autowired UserRepository users;
    @Autowired VisionSessionRepository sessions;
    @Autowired VisionPollingRepository polling;
    @Autowired KeypadTriggerPort keypad;
    @Autowired StreamingAgentRepository agentJobs;
    @Autowired StreamingAgentEventPort streamingEvents;
    @Autowired ProcessStreamingAgentService streamingWorker;
    @Autowired VisionFallbackRepository jobs;
    @Autowired ProcessVisionFallbackService worker;
    @Autowired com.omniguardy.backend.domain.visionsession.application.port.in.CompleteVisionSessionInputPort completion;
    @Autowired PollStreamingVisionService pollingWorker;
    @Autowired VisionFallbackEventPort events;
    @Autowired VisionFallbackPolicy policy;
    @Autowired StreamingSessionVideoInputPort videos;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AnalyzeAgentRiskUseCase agent;
    @Autowired AgentExecutionGuard guard;
    MutableClock time;
    User user;
    java.sql.Connection schema;
    final Map<String,byte[]> objects=new ConcurrentHashMap<>();
    final List<MediaFile> downloaded=new CopyOnWriteArrayList<>();
    @BeforeAll void migrations() throws Exception {
        schema=Objects.requireNonNull(jdbc.getDataSource()).getConnection();
        for(String migration:List.of("V6__create_vision_sessions.sql","V7__create_vision_early_events.sql",
                "V8__create_vision_polling_and_final_results.sql","V9__create_streaming_agent_jobs.sql",
                "V10__create_streaming_session_videos.sql","V11__create_vision_fallback_jobs.sql")) {
            String ddl=Files.readString(Path.of("src/main/resources/db/migration/"+migration))
                    .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin","").replace(" CHARACTER SET ascii COLLATE ascii_bin","");
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8))).populate(schema);
        }
    }
    @AfterAll void close() throws Exception { schema.close(); }
    @BeforeEach void setup() throws Exception {
        for(String table:List.of("security_agent_executions","vision_fallback_jobs","streaming_session_videos","streaming_agent_jobs",
                "vision_final_results","vision_polling_jobs","vision_early_events","vision_sessions","keypad_camera_triggers","security_events","users")) jdbc.update("DELETE FROM "+table);
        objects.clear(); downloaded.clear(); time=new MutableClock(); when(clock.instant()).thenAnswer(call -> time.instant());
        user=users.save(User.builder().email("fallback@test.invalid").name("test").password("synthetic").phoneNumber("test").build());
        when(owner.resolve()).thenReturn(Optional.of(user));
        when(s3.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenAnswer(call -> {
            PutObjectRequest request=call.getArgument(0); RequestBody body=call.getArgument(1);
            try(var in=body.contentStreamProvider().newStream()) {
                if(objects.putIfAbsent(request.key(),in.readAllBytes())!=null) throw S3Exception.builder().statusCode(412).build();
            }
            return PutObjectResponse.builder().build();
        });
        when(s3.getObject(any(GetObjectRequest.class))).thenAnswer(call -> {
            GetObjectRequest request=call.getArgument(0);
            assertEquals("test-bucket",request.bucket()); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ResponseInputStream<>(GetObjectResponse.builder().build(),AbortableInputStream.create(new ByteArrayInputStream(objects.get(request.key()))));
        });
        when(vision.analyze(any(),any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            MediaFile file=call.getArgument(0); downloaded.add(file); assertArrayEquals(StreamingMp4TestSupport.mp4(1),file.bytes());
            return analysis(call.getArgument(1),true);
        });
        when(risk.assess(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return new AgentAssessment(RiskLevel.LOW,72,"existing assessment");
        });
    }
    @Test void registeredSessionAndStoredMp4AloneDoNotStartPollingOrFallback() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> keypad.record("trigger",OffsetDateTime.ofInstant(time.instant(),ZoneOffset.UTC)));
        sessions.insert(VisionSession.register("session",VisionTriggerType.KEYPAD,null,"trigger"));
        upload("session"); time.advance(Duration.ofMinutes(20)); worker.processDue(); pollingWorker.pollDue();
        assertTrue(polling.find("session").isEmpty()); assertTrue(jobs.find("session").isEmpty());
        verifyNoInteractions(streaming,vision,risk,notifications,sound);
    }
    @Test void springCompletionQueuesPollingThenObservedVisionFailureStartsFallback() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> keypad.record("trigger",OffsetDateTime.ofInstant(time.instant(),ZoneOffset.UTC)));
        sessions.insert(VisionSession.register("session",VisionTriggerType.KEYPAD,null,"trigger")); upload("session");
        when(streaming.status("session")).thenReturn(new com.omniguardy.backend.domain.visionsession.infrastructure.streaming.StreamingVisionJsonMapper()
                .status(statusJson("session","trigger","KEYPAD",null).replace("COMPLETED","FAILED").replace("\"finalResultAvailable\":true","\"finalResultAvailable\":false")));
        completion.complete("session"); worker.processDue(); assertTrue(jobs.find("session").isEmpty());
        pollingWorker.pollDue(); assertEquals(VisionProcessingState.FALLBACK_REQUIRED,polling.find("session").orElseThrow().state());
        worker.processDue(); assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state());
        verify(streaming).status("session"); verify(streaming,never()).result(anyString()); verify(vision).analyze(any(),any()); verify(risk).assess(any());
    }

    @Test void partialKeypadUsesOriginalMp4ExistingAgentAndResponseOnce() throws Exception {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("session");
        String raw=jdbc.queryForObject("SELECT raw_result_json FROM vision_final_results",String.class);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> worker.processDue()); worker.processDue();
        var job=jobs.find("session").orElseThrow(); assertEquals(VisionFallbackJob.State.SUCCEEDED,job.state());
        assertEquals(1,job.visionAttempts()); assertEquals(1,job.agentAttempts()); assertEquals(1,countEvents());
        assertEquals(UUID.nameUUIDFromBytes("KEYPAD:trigger-session".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),job.eventId());
        assertEquals("HIGH",securityEvents.findByEventId(job.eventId()).orElseThrow().getRiskLevel());
        assertEquals(raw,jdbc.queryForObject("SELECT raw_result_json FROM vision_final_results",String.class));
        assertEquals("FALLBACK_REQUIRED",polling.find("session").orElseThrow().state().name());
        assertEquals(job.eventId(),videos.find("session").securityEventId());
        verify(vision).analyze(any(),any()); verify(risk).assess(any()); verify(sound).play(PlaySoundType.SIREN);
        verify(notifications).sendSecurityAlert(any(),eq(3),eq("KEYPAD"),eq(job.eventId()));
        verify(s3).putObject(any(PutObjectRequest.class),any(RequestBody.class)); verifyNoInteractions(streaming);
        for(MediaFile file:downloaded) assertThrows(IOException.class,file::openStream);
    }
    @Test void fallbackPerformanceLogMeasuresOnlyExecutedVisionAttemptWithSessionBinding() {
        seed("observed",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("observed");
        try (var logs = new com.omniguardy.backend.global.performance.PerformanceLogCapture()) {
            worker.processDue(); worker.processDue();
            var visionLog = logs.stage("FALLBACK_MP4_VISION");
            assertEquals("observed",visionLog.path("sessionId").asText()); assertEquals("trigger-observed",visionLog.path("triggerId").asText());
            assertEquals("KEYPAD",visionLog.path("triggerType").asText()); assertEquals(1,visionLog.path("attempt").asInt());
            assertEquals("SUCCESS",visionLog.path("status").asText());
            assertEquals(1,logs.events().stream().filter(e -> "FALLBACK_MP4_VISION".equals(e.path("stage").asText())).count());
            assertEquals("observed",logs.stage("AGENT_EXECUTION").path("sessionId").asText());
        }
        verify(vision).analyze(any(),any()); verify(risk).assess(any());
    }

    @Test void failedAudioReusesEventAndPreservesAudioEvidence() {
        seed("audio",VisionTriggerType.AUDIO,VisionFinalOutcome.FAILED); upload("audio"); worker.processDue();
        var event=securityEvents.findByEventId("audio-event").orElseThrow();
        assertEquals(1,countEvents()); assertEquals(AudioEventType.KNOCK,event.getAudioEventType()); assertEquals(0.96,event.getAudioProbability());
        assertEquals("audio-event",jobs.find("audio").orElseThrow().eventId()); assertNotNull(event.getVideoPath());
        verify(risk).assess(any()); verify(notifications).sendSecurityAlert(any(),eq(2),eq("AUDIO"),eq("audio-event"));
        verify(sound).play(PlaySoundType.WARNING);
    }
    @Test void temporalAudioKeepsPredictionProbabilitiesAndExistingRiskDecision() {
        doAnswer(call -> {
            VideoTriggerContext context=call.getArgument(1); var base=analysis(context,false);
            String prediction=context.securityEventId().startsWith("risk") ? "A18" : "N1";
            return new VisionAnalysis(base.triggerId(),base.triggerType(),base.securityEventId(),base.triggeredAt(),base.analyzedAt(),base.video(),
                    new VisionBehavior(prediction,"test",0.91,Map.of(prediction,0.91)),List.of(),new VisionObservations(1,true,true,"temporal_trial6"));
        }).when(vision).analyze(any(),any());
        seed("risk",VisionTriggerType.AUDIO,VisionFinalOutcome.FAILED); upload("risk");
        seed("safe",VisionTriggerType.AUDIO,VisionFinalOutcome.FAILED); upload("safe"); worker.processDue();
        var input=org.mockito.ArgumentCaptor.forClass(AgentContext.class); verify(risk).assess(input.capture());
        assertEquals("A18",input.getValue().vision().prediction()); assertEquals(0.91,input.getValue().vision().confidence());
        assertEquals(Map.of("A18",0.91),input.getValue().vision().classProbabilities());
        assertEquals("COMPLETED",securityEvents.findByEventId("safe-event").orElseThrow().getStatus());
        assertEquals(0,jobs.find("safe").orElseThrow().agentAttempts()); assertEquals(2,countEvents());
    }
    @Test void nullMp4PersonCountIsNotInventedAndActualZeroIsValid() {
        doAnswer(call -> {
            VideoTriggerContext context=call.getArgument(1); var base=analysis(context,true);
            return new VisionAnalysis(base.triggerId(),base.triggerType(),base.securityEventId(),base.triggeredAt(),base.analyzedAt(),base.video(),base.behavior(),base.visionEvents(),
                    new VisionObservations(context.triggerId().contains("unknown") ? null : 0,true,true,"exhibition_rules"));
        }).when(vision).analyze(any(),any());
        seed("unknown",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("unknown");
        seed("zero",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("zero"); worker.processDue();
        assertEquals(VisionFallbackJob.State.FAILED,jobs.find("unknown").orElseThrow().state());
        var input=org.mockito.ArgumentCaptor.forClass(AgentContext.class); verify(risk).assess(input.capture());
        assertEquals(0,input.getValue().vision().personCount()); assertEquals(1,countEvents());
    }
    @Test void rejectedAndMissingFinalTimeoutAreEligible() {
        seed("rejected",VisionTriggerType.KEYPAD,VisionFinalOutcome.REJECTED); upload("rejected");
        seed("timeout",VisionTriggerType.KEYPAD,null); upload("timeout"); worker.processDue();
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("rejected").orElseThrow().state());
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("timeout").orElseThrow().state());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM vision_final_results",Integer.class));
    }
    @Test void blockedUnknownCountTransfersToFallbackWithoutOverwritingV9() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.VALID);
        jdbc.update("UPDATE vision_final_results SET raw_result_json=REPLACE(raw_result_json,'\"trackedPersonCount\":0','\"trackedPersonCount\":null')");
        streamingWorker.processDue(); assertEquals("UNKNOWN_PERSON_COUNT",agentJobs.find("session").orElseThrow().reason());
        upload("session"); worker.processDue();
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state());
        assertEquals(StreamingAgentJob.State.BLOCKED,agentJobs.find("session").orElseThrow().state());
        assertEquals(0,agentJobs.find("session").orElseThrow().agentAttempts()); verify(risk).assess(any());
    }
    @Test void validResultsAndAgentUncertaintyAreNeverFallbackCandidates() {
        seed("valid",VisionTriggerType.KEYPAD,VisionFinalOutcome.VALID); upload("valid"); worker.processDue();
        assertTrue(jobs.find("valid").isEmpty());
        agentJobs.enqueue("valid");
        jdbc.update("UPDATE streaming_agent_jobs SET processing_state='UNCERTAIN',reason='NETWORK_OUTCOME_UNKNOWN',fallback_required=FALSE");
        worker.processDue(); assertTrue(jobs.find("valid").isEmpty()); verifyNoInteractions(vision,risk);
    }
    @Test void validStreamingAssessmentPreventsFallbackForever() {
        seed("valid",VisionTriggerType.KEYPAD,VisionFinalOutcome.VALID);
        jdbc.update("UPDATE vision_final_results SET raw_result_json=REPLACE(raw_result_json,'\"visionEvents\":[]','\"visionEvents\":[{\"eventType\":\"SILENT_SIGNAL\",\"detectedFrame\":114,\"confirmedFrame\":114,\"capturedAt\":\"3.8\"}]')");
        streamingWorker.processDue(); upload("valid"); worker.processDue();
        assertEquals(StreamingAgentJob.State.SUCCEEDED,agentJobs.find("valid").orElseThrow().state());
        assertTrue(jobs.find("valid").isEmpty()); verifyNoInteractions(vision); verify(risk).assess(any());
    }
    @Test void waitsForVideoAndThenRunsWhenUploadArrives() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); worker.processDue();
        assertEquals(VisionFallbackJob.State.WAITING_VIDEO,jobs.find("session").orElseThrow().state()); verifyNoInteractions(vision,risk,s3);
        upload("session"); time.advance(Duration.ofSeconds(1)); worker.processDue();
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state());
    }
    @Test void missingVideoHasFiniteDeadlineWithoutAgentOrVision() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); worker.processDue(); time.advance(Duration.ofSeconds(11)); worker.processDue();
        assertEquals(VisionFallbackJob.State.FAILED,jobs.find("session").orElseThrow().state());
        assertEquals("VIDEO_WAIT_TIMEOUT",jobs.find("session").orElseThrow().reason()); verifyNoInteractions(vision,risk,s3);
    }
    @Test void videoUploadedAfterDeadlineDoesNotBypassWaitingLimit() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); worker.processDue();
        time.advance(Duration.ofSeconds(11)); upload("session"); worker.processDue();
        assertEquals(VisionFallbackJob.State.FAILED,jobs.find("session").orElseThrow().state());
        assertEquals("VIDEO_WAIT_TIMEOUT",jobs.find("session").orElseThrow().reason()); verifyNoInteractions(vision,risk);
    }
    @Test void keypadWithoutDetectionsCreatesNoEventAndAudioWithoutRiskUsesExistingCompletionRule() {
        doAnswer(call -> analysis(call.getArgument(1),false)).when(vision).analyze(any(),any());
        seed("keypad",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("keypad");
        seed("audio",VisionTriggerType.AUDIO,VisionFinalOutcome.FAILED); upload("audio"); worker.processDue();
        assertNull(jobs.find("keypad").orElseThrow().eventId()); assertEquals(1,countEvents());
        assertEquals("COMPLETED",securityEvents.findByEventId("audio-event").orElseThrow().getStatus());
        verifyNoInteractions(risk,notifications,sound);
    }
    @Test void sourceAndVisionFailuresRetryBoundedlyAndPermanentContractFailureDoesNot() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("session");
        doThrow(S3Exception.builder().statusCode(503).build()).when(s3).getObject(any(GetObjectRequest.class));
        worker.processDue(); assertEquals(VisionFallbackJob.State.PENDING,jobs.find("session").orElseThrow().state());
        time.advance(Duration.ofSeconds(1)); worker.processDue(); time.advance(Duration.ofSeconds(1)); worker.processDue();
        assertEquals(VisionFallbackJob.State.FAILED,jobs.find("session").orElseThrow().state()); assertEquals(3,jobs.find("session").orElseThrow().visionAttempts());
        verifyNoInteractions(vision,risk);
    }
    @Test void visionTimeoutRetriesThenUsesCachedResultAfterPreparationRestart() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("session");
        doThrow(new UncheckedIOException(new java.net.SocketTimeoutException("test timeout")))
                .doAnswer(call -> analysis(call.getArgument(1),true)).when(vision).analyze(any(),any());
        worker.processDue(); time.advance(Duration.ofSeconds(1)); worker.processDue();
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state()); verify(vision,times(2)).analyze(any(),any());
    }
    @Test void invalidMp4ResultIsTerminalAndBlockedUnsupportedStreamingEventFallsBack() {
        seed("invalid",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("invalid");
        doAnswer(call -> analysis(new VideoTriggerContext(null,"different-trigger",TriggerType.KEYPAD,null,time.instant().atOffset(ZoneOffset.UTC).toString()),true))
                .when(vision).analyze(any(),any());
        worker.processDue(); time.advance(Duration.ofSeconds(2)); worker.processDue();
        assertEquals(VisionFallbackJob.State.FAILED,jobs.find("invalid").orElseThrow().state());
        assertEquals(1,jobs.find("invalid").orElseThrow().visionAttempts()); verifyNoInteractions(risk);
        seed("unsupported",VisionTriggerType.KEYPAD,VisionFinalOutcome.VALID);
        jdbc.update("UPDATE vision_final_results SET raw_result_json=REPLACE(raw_result_json,'\"visionEvents\":[]','\"visionEvents\":[{\"eventType\":\"FUTURE_EVENT\"}]') WHERE session_id='unsupported'");
        streamingWorker.processDue(); assertEquals("UNSUPPORTED_EVENT",agentJobs.find("unsupported").orElseThrow().reason());
        doAnswer(call -> analysis(call.getArgument(1),true)).when(vision).analyze(any(),any());
        upload("unsupported"); worker.processDue();
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("unsupported").orElseThrow().state()); verify(risk).assess(any());
    }
    @Test void concurrentWorkersUseOneVisionAndOneAgent() throws Exception {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("session");
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertTrue(release.await(10,TimeUnit.SECONDS)); return analysis(call.getArgument(1),true); }).when(vision).analyze(any(),any());
        var pool=Executors.newFixedThreadPool(3);
        try {
            var first=pool.submit(worker::processDue); assertTrue(entered.await(10,TimeUnit.SECONDS));
            var second=pool.submit(worker::processDue); var third=pool.submit(streamingWorker::processDue);
            second.get(10,TimeUnit.SECONDS); third.get(10,TimeUnit.SECONDS); release.countDown(); first.get(10,TimeUnit.SECONDS);
            verify(vision).analyze(any(),any()); verify(risk).assess(any()); verify(sound).play(any()); assertEquals(1,countEvents());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void expiredVisionWorkerIsFencedAndRestartReusesCachedVision() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("session"); jobs.discover(policy);
        var old=jobs.claim("session",policy).orElseThrow(); var source=jobs.source(old);
        assertTrue(jobs.beginVision(old,source.video().orElseThrow(),3)); assertTrue(jobs.analyzed(old,analysis(source.context(),true)));
        time.advance(Duration.ofMinutes(6)); worker.processDue();
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state());
        assertFalse(jobs.analyzed(old,analysis(source.context(),false))); verifyNoInteractions(vision); verify(risk).assess(any());
    }
    @Test void preparedRestartKeepsEventIdentityAndDoesNotReanalyzeMp4() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("session"); jobs.discover(policy);
        var claim=jobs.claim("session",policy).orElseThrow(); var source=jobs.source(claim); var result=analysis(source.context(),true);
        jobs.beginVision(claim,source.video().orElseThrow(),3); jobs.analyzed(claim,result); events.prepare(claim,result,source);
        String id=jobs.find("session").orElseThrow().eventId(); time.advance(Duration.ofMinutes(6)); worker.processDue();
        assertEquals(id,jobs.find("session").orElseThrow().eventId()); assertEquals(1,countEvents());
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state());
        assertFalse(jobs.startCalling(claim,policy)); verifyNoInteractions(vision); verify(risk).assess(any());
    }
    @Test void activeV9OwnsSessionAndOnlyUncalledBlockedJobCanTransferOwnership() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.VALID); upload("session"); agentJobs.enqueue("session");
        var v9=agentJobs.claim("session",Duration.ofMinutes(1)).orElseThrow(); worker.processDue();
        assertTrue(jobs.find("session").isEmpty()); verifyNoInteractions(vision,risk);
        agentJobs.finish(v9,StreamingAgentJob.State.BLOCKED,"UNKNOWN_PERSON_COUNT","NOT_STARTED");
        jobs.discover(policy); assertTrue(agentJobs.claim("session",Duration.ofMinutes(1)).isEmpty());
        worker.processDue(); assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state());
        assertEquals(StreamingAgentJob.State.BLOCKED,agentJobs.find("session").orElseThrow().state()); verify(risk).assess(any());
    }
    @Test void restartAfterCallingOrAgentNetworkErrorNeverRepeatsAgentOrNotifications() {
        var calling=prepareCalling("restart"); time.advance(Duration.ofMinutes(6)); worker.processDue(); worker.processDue();
        assertEquals(VisionFallbackJob.State.UNCERTAIN,jobs.find("restart").orElseThrow().state()); verifyNoInteractions(risk,notifications,sound);
        assertThrows(com.omniguardy.backend.domain.securityevent.domain.error.AgentExecutionConflict.class,() -> agent.analyze(calling.eventId()));
        seed("network",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("network");
        doThrow(new UncheckedIOException(new IOException("unknown outcome"))).when(risk).assess(any());
        worker.processDue(); time.advance(Duration.ofHours(1)); worker.processDue();
        assertEquals(VisionFallbackJob.State.UNCERTAIN,jobs.find("network").orElseThrow().state()); verify(risk).assess(any()); verifyNoInteractions(notifications,sound);
    }
    @Test void committedAgentResultRecoversWithoutReplayingResponses() {
        var calling=prepareCalling("session"); events.analyze(calling); time.advance(Duration.ofMinutes(6)); worker.processDue();
        assertEquals(VisionFallbackJob.State.SUCCEEDED,jobs.find("session").orElseThrow().state()); verify(risk).assess(any()); verify(sound).play(any());
        agent.analyze(calling.eventId()); verify(risk).assess(any()); verify(sound).play(any());
    }
    @Test void preexistingLegacyAnalysisIsNeverOverwrittenOrAssessedAgain() {
        seed("audio",VisionTriggerType.AUDIO,VisionFinalOutcome.FAILED); upload("audio");
        var event=securityEvents.findByEventId("audio-event").orElseThrow();
        event.updateVisionResult("A18",0.98,"temporal_trial6","{}","[]",1,7.0,OffsetDateTime.now());
        event.updateAgentResult("HIGH",99,"existing legacy"); securityEvents.save(event); worker.processDue();
        assertEquals(VisionFallbackJob.State.SKIPPED,jobs.find("audio").orElseThrow().state());
        assertEquals(99,securityEvents.findByEventId("audio-event").orElseThrow().getRiskScore()); verifyNoInteractions(vision,risk,notifications,sound);
    }
    @Test void repeatedPreparationDatabaseFailureIsBoundedAndDoesNotRepeatVisionOrCreateEvents() {
        seed("session",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("session");
        jdbc.execute("ALTER TABLE vision_fallback_jobs ADD CONSTRAINT test_fail_preparation CHECK (processing_state<>'PREPARED')");
        try {
            worker.processDue(); time.advance(Duration.ofSeconds(1)); worker.processDue(); time.advance(Duration.ofSeconds(1)); worker.processDue();
            assertEquals(VisionFallbackJob.State.FAILED,jobs.find("session").orElseThrow().state());
            assertEquals(3,jobs.find("session").orElseThrow().attempts()); assertEquals(1,jobs.find("session").orElseThrow().visionAttempts());
            assertEquals(0,countEvents()); verify(vision).analyze(any(),any()); verifyNoInteractions(risk,notifications,sound);
        } finally { jdbc.execute("ALTER TABLE vision_fallback_jobs DROP CONSTRAINT test_fail_preparation"); }
    }
    @Test void eventReservationBlocksLegacyAgentDuringFallbackAndTransactionalPreparationRollsBack() {
        var calling=prepareCalling("session");
        assertThrows(com.omniguardy.backend.domain.securityevent.domain.error.AgentExecutionConflict.class,() -> agent.analyze(calling.eventId()));
        events.analyze(calling); verify(risk).assess(any());
        seed("rollback",VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload("rollback"); jobs.discover(policy);
        var claim=jobs.claim("rollback",policy).orElseThrow(); var source=jobs.source(claim); var result=analysis(source.context(),true);
        jobs.beginVision(claim,source.video().orElseThrow(),3); jobs.analyzed(claim,result);
        jdbc.execute("ALTER TABLE vision_fallback_jobs ADD CONSTRAINT test_no_prepared CHECK (processing_state<>'PREPARED')");
        try { assertThrows(DataIntegrityViolationException.class,() -> events.prepare(claim,result,source)); }
        finally { jdbc.execute("ALTER TABLE vision_fallback_jobs DROP CONSTRAINT test_no_prepared"); }
        assertEquals(1,countEvents()); assertNull(jobs.find("rollback").orElseThrow().eventId());
    }
    VisionFallbackJob prepareCalling(String id) {
        seed(id,VisionTriggerType.KEYPAD,VisionFinalOutcome.PARTIAL); upload(id); jobs.discover(policy);
        var claim=jobs.claim(id,policy).orElseThrow(); var source=jobs.source(claim); var result=analysis(source.context(),true);
        jobs.beginVision(claim,source.video().orElseThrow(),3); jobs.analyzed(claim,result); events.prepare(claim,result,source);
        assertTrue(jobs.startCalling(jobs.find(id).orElseThrow(),policy)); return jobs.find(id).orElseThrow();
    }
    void seed(String id,VisionTriggerType type,VisionFinalOutcome outcome) {
        String trigger="trigger-"+id,eventId=null;
        if(type==VisionTriggerType.KEYPAD) new TransactionTemplate(transactions).executeWithoutResult(tx -> keypad.record(trigger,OffsetDateTime.ofInstant(time.instant(),ZoneOffset.UTC)));
        else {
            eventId=id+"-event";
            securityEvents.save(SecurityEvent.builder().eventId(eventId).triggerId(trigger).triggerType(EventTriggerType.AUDIO)
                    .triggeredAt(OffsetDateTime.ofInstant(time.instant(),ZoneOffset.UTC)).status("CAMERA_REQUESTED").user(user)
                    .audioEventType(AudioEventType.KNOCK).audioProbability(0.96).audioPath("original.wav").build());
        }
        sessions.insert(VisionSession.register(id,type,eventId,type==VisionTriggerType.KEYPAD ? trigger : null));
        polling.enqueue(new VisionBinding(id,type,trigger,eventId),Duration.ofMinutes(5));
        var claim=polling.claim(id,Duration.ofSeconds(30)).orElseThrow();
        polling.finish(claim,outcome==null ? null : result(id,trigger,type.name(),eventId),outcome==null ? VisionFinalOutcome.FAILED : outcome,"test",null,null);
    }
    void upload(String id) { videos.upload(id,StreamingMp4TestSupport.file(1)); }
    VisionAnalysis analysis(VideoTriggerContext context,boolean detected) {
        return new VisionAnalysis(context.triggerId(),context.triggerType(),context.securityEventId(),OffsetDateTime.parse(context.triggeredAt()),
                OffsetDateTime.ofInstant(time.instant(),ZoneOffset.UTC),new VisionVideoInfo("recording.mp4",7.0,30.0,210),null,
                detected ? List.of(new VisionEvent(context.triggerType()==TriggerType.AUDIO ? "CAMERA_OCCLUSION_SUSPECTED" : "SILENT_SIGNAL",0.99,114,Map.of())) : List.of(),
                new VisionObservations(1,true,true,"exhibition_rules"));
    }
    int countEvents() { return jdbc.queryForObject("SELECT COUNT(*) FROM security_events",Integer.class); }
}
