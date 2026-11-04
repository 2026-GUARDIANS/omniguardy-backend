package com.omniguardy.backend.domain.securityevent;

import com.omniguardy.backend.domain.securityevent.application.port.out.*;
import com.omniguardy.backend.domain.securityevent.application.usecase.*;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.infrastructure.persistence.JdbcSecurityResponseLedger;
import com.omniguardy.backend.domain.securityevent.infrastructure.response.EarlyWarningWorkers;
import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.ReceiveVisionEarlyEventService;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.persistence.*;
import com.omniguardy.backend.global.performance.PerformanceLogCapture;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.omniguardy.backend.domain.securityevent.application.port.out.SecurityResponseLedger.Channel.*;

class SilentSignalEarlyWarningIntegrationTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    SecurityResponseLedger ledger;
    DispatchEarlyWarningService dispatch;
    EarlyWarningDeliveryPort delivery;
    ReceiveVisionEarlyEventService receive;
    NotificationService notifications;
    PlaySoundUseCase sounds;
    RiskResponseService finalResponse;
    java.sql.Connection schemaConnection;

    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = context.getBean(JdbcTemplate.class);
        schemaConnection = context.getBean(DataSource.class).getConnection();
        jdbc.execute("CREATE TABLE security_events (event_id VARCHAR(36) NOT NULL UNIQUE, trigger_id VARCHAR(64), trigger_type VARCHAR(20))");
        migrate("V6__create_vision_sessions.sql");
        migrate("V7__create_vision_early_events.sql");
        migrate("V12__create_security_response_ledger.sql");
        ledger = context.getBean(SecurityResponseLedger.class);
        dispatch = context.getBean(DispatchEarlyWarningService.class);
        delivery = context.getBean(EarlyWarningDeliveryPort.class);
        when(delivery.sendPush(any())).thenReturn("REQUEST_ACCEPTED");
        receive = new ReceiveVisionEarlyEventService(context.getBean(VisionSessionRepository.class),
                context.getBean(KeypadTriggerPort.class), context.getBean(VisionAudioTriggerPort.class),
                context.getBean(VisionEarlyEventRepository.class), dispatch::enqueue);
        jdbc.update("INSERT INTO security_events VALUES ('event','audio-trigger','AUDIO')");
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("audio-session", VisionTriggerType.AUDIO, "event", null));
        jdbc.update("INSERT INTO keypad_camera_triggers VALUES ('keypad-trigger',?)", LocalDateTime.now(ZoneOffset.UTC));
        context.getBean(VisionSessionRepository.class).insert(VisionSession.register("keypad-session", VisionTriggerType.KEYPAD, null, "keypad-trigger"));
        when(context.getBean(VisionAudioTriggerPort.class).findIssuedTriggerId("event")).thenReturn(Optional.of("audio-trigger"));
        notifications = mock(NotificationService.class); sounds = mock(PlaySoundUseCase.class);
        finalResponse = new RiskResponseService(notifications, sounds); finalResponse.setResponseLedger(ledger);
    }

    @AfterEach void close() throws Exception { schemaConnection.close(); context.close(); }

    void migrate(String file) throws Exception {
        String ddl = Files.readString(Path.of("src/main/resources/db/migration/" + file))
                .replace(" CHARACTER SET utf8mb4 COLLATE utf8mb4_bin", "")
                .replace("UTC_TIMESTAMP(6)", "CURRENT_TIMESTAMP(6)");
        new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(
                ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8))).populate(schemaConnection);
    }

    VisionEarlyEvent early(boolean keypad, String instance) {
        return new VisionEarlyEvent(keypad ? "keypad-session" : "audio-session", keypad ? "keypad-trigger" : "audio-trigger",
                keypad ? null : "event", keypad ? VisionTriggerType.KEYPAD : VisionTriggerType.AUDIO,
                instance, 1, 100, 114, "3.8", "2026-11-04T02:00:00Z");
    }
    SecurityEvent event(boolean keypad) {
        return SecurityEvent.builder().eventId(keypad ? "keypad-event" : "event")
                .triggerId(keypad ? "keypad-trigger" : "audio-trigger")
                .triggerType(keypad ? EventTriggerType.KEYPAD : EventTriggerType.AUDIO)
                .user(mock(User.class)).status("COMPLETED").riskLevel("HIGH").build();
    }
    void sendBoth() { dispatch.dispatch(FCM); dispatch.dispatch(SOUND); }
    String state(String channel) { return jdbc.queryForObject("SELECT " + channel + "_state FROM security_response_ledger", String.class); }

    @Test void audioDispatchesWithoutCompleteVisionAgentOrMp4AndKeepsSessionAndEventUntouched() {
        try (var logs = new PerformanceLogCapture()) {
            receive.receive(early(false, "one")); sendBoth();
            var claim = org.mockito.ArgumentCaptor.forClass(SecurityResponseLedger.Binding.class);
            verify(delivery).sendPush(claim.capture()); verify(delivery).sendSiren();
            assertEquals("event", claim.getValue().eventId());
            assertEquals("audio-session", claim.getValue().sessionId());
            assertEquals("REGISTERED", jdbc.queryForObject("SELECT status FROM vision_sessions WHERE session_id='audio-session'", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
            for (String stage : List.of("EARLY_EVENT_FCM_REQUEST", "EARLY_EVENT_MQTT_SIREN", "EARLY_EVENT_TO_FCM", "EARLY_EVENT_TO_SOUND")) {
                var log = logs.stage(stage);
                assertEquals("SPRING", log.path("source").asText());
                assertEquals("event", log.path("eventId").asText());
                assertTrue(log.path("durationMs").asDouble() >= 0);
            }
            assertEquals("MONOTONIC", logs.stage("EARLY_EVENT_FCM_REQUEST").path("timingBasis").asText());
            assertEquals("PERSISTED_WALL_CLOCK", logs.stage("EARLY_EVENT_TO_SOUND").path("timingBasis").asText());
            assertFalse(logs.stage("EARLY_EVENT_TO_SOUND").path("recipientDeliveryConfirmed").asBoolean());
        }
    }

    @Test void keypadWarnsByValidatedTriggerWithoutCreatingEventThenLinksTheRealFinalEvent() {
        receive.receive(early(true, "one")); sendBoth();
        verify(delivery).sendPush(argThat(b -> b.eventId() == null && b.triggerId().equals("keypad-trigger")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM security_events WHERE trigger_type='KEYPAD'", Integer.class));
        jdbc.update("INSERT INTO security_events VALUES ('keypad-event','keypad-trigger','KEYPAD')");
        dispatch.recover();
        assertEquals("keypad-event", jdbc.queryForObject("SELECT event_id FROM security_response_ledger", String.class));
        finalResponse.respond(event(true), RiskLevel.HIGH);
        verifyNoInteractions(notifications, sounds);
        assertNull(jdbc.queryForObject("SELECT security_event_id FROM vision_early_events", String.class));
    }

    @Test void repeatedAndDifferentInstancesProduceOneResponsePerTriggerEvenAfterServiceRecreation() {
        receive.receive(early(false, "one")); sendBoth();
        receive.receive(early(false, "one")); receive.receive(early(false, "two"));
        new DispatchEarlyWarningService(ledger, delivery).recover(); sendBoth();
        verify(delivery, times(1)).sendPush(any()); verify(delivery, times(1)).sendSiren();
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class));
    }

    @Test void concurrentCallbacksAndWorkersClaimEachChannelOnlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var barrier = new CyclicBarrier(8);
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(() -> { barrier.await(10, TimeUnit.SECONDS);
                receive.receive(early(false, "one")); sendBoth(); return null; });
            for (var result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) result.get();
            verify(delivery, times(1)).sendPush(any()); verify(delivery, times(1)).sendSiren();
        } finally { pool.shutdownNow(); }
    }

    @Test void pushFailureDoesNotBlockSirenAndUncertainDeliveryIsNotBlindlyRetriedByCallbackOrAgent() {
        when(delivery.sendPush(any())).thenThrow(new IllegalStateException("lost provider reply"));
        receive.receive(early(false, "one")); sendBoth();
        assertEquals("UNCERTAIN", state("fcm")); assertEquals("SUCCEEDED", state("sound"));
        receive.receive(early(false, "one")); sendBoth(); finalResponse.respond(event(false), RiskLevel.HIGH);
        verify(delivery, times(1)).sendPush(any()); verify(delivery, times(1)).sendSiren();
        verifyNoInteractions(notifications, sounds);
    }

    @Test void sirenFailureDoesNotBlockFcmAndTimeoutIsLogged() {
        doThrow(new IllegalStateException(new java.net.SocketTimeoutException())).when(delivery).sendSiren();
        try (var logs = new PerformanceLogCapture()) {
            receive.receive(early(false, "one")); dispatch.dispatch(SOUND); dispatch.dispatch(FCM);
            assertEquals("SUCCEEDED", state("fcm")); assertEquals("UNCERTAIN", state("sound"));
            assertEquals("TIMEOUT", logs.stage("EARLY_EVENT_MQTT_SIREN").path("status").asText());
        }
    }

    @Test void finalHighResponseBeforeLateCallbackPreventsDuplicateEarlyDelivery() {
        finalResponse.respond(event(false), RiskLevel.HIGH);
        receive.receive(early(false, "late")); sendBoth();
        verify(notifications).sendSecurityAlert(any(), eq(3), eq("AUDIO"), eq("event"));
        verify(sounds).play(PlaySoundType.SIREN); verifyNoInteractions(delivery);
    }

    @Test void pendingEarlyWarningAlsoSuppressesFinalResponseAndLowDoesNotCancelWarning() {
        receive.receive(early(false, "one"));
        finalResponse.respond(event(false), RiskLevel.MEDIUM); finalResponse.respond(event(false), RiskLevel.LOW);
        verifyNoInteractions(notifications, sounds);
        sendBoth(); verify(delivery).sendSiren();
    }

    @Test void finalMediumBeforeEarlyWarningCanEscalateOnceAndOldCompletionCannotOverwriteNewClaim() {
        var b = new SecurityResponseLedger.Binding("AUDIO:audio-trigger", "event", "audio-trigger", null, "AUDIO", null);
        var original = ledger.claimFinal(b, SOUND, 2).orElseThrow();
        receive.receive(early(false, "one"));
        var early = ledger.claimEarly(b.key(), SOUND).orElseThrow();
        ledger.finished(original, "SUCCEEDED"); assertEquals("CALLING", state("sound"));
        ledger.finished(early, "SUCCEEDED");
        assertTrue(ledger.claimFinal(b, SOUND, 3).isEmpty());
    }

    @Test void committedObservationWithoutQueueAndPendingWithoutWorkerRecoverAfterRestart() {
        context.getBean(VisionEarlyEventRepository.class).insert(early(false, "one"));
        dispatch.recover(); assertEquals("PENDING", state("fcm"));
        new DispatchEarlyWarningService(ledger, delivery).dispatch(FCM);
        dispatch.dispatch(SOUND); verify(delivery).sendPush(any()); verify(delivery).sendSiren();
    }

    @Test void callbackRetriesRepairQueueFailureAfterOriginalObservationCommitted() {
        var broken = new ReceiveVisionEarlyEventService(context.getBean(VisionSessionRepository.class),
                context.getBean(KeypadTriggerPort.class), context.getBean(VisionAudioTriggerPort.class),
                context.getBean(VisionEarlyEventRepository.class), session -> { throw new IllegalStateException("queue unavailable"); });
        assertThrows(IllegalStateException.class, () -> broken.receive(early(false, "one")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class));
        receive.receive(early(false, "one")); sendBoth();
        verify(delivery).sendPush(any()); verify(delivery).sendSiren();
    }

    @Test void completedAudioEventAndCompletedSessionStillAcceptWarning() {
        jdbc.update("UPDATE vision_sessions SET status='COMPLETED' WHERE session_id='audio-session'");
        receive.receive(early(false, "one")); sendBoth();
        verify(delivery).sendSiren();
    }

    @Test void skippedPushCanBeRecoveredByFinalResponseWithoutRepeatingSiren() {
        when(delivery.sendPush(any())).thenReturn("NO_ACTIVE_TOKENS");
        try (var logs = new PerformanceLogCapture()) {
            receive.receive(early(false, "one")); sendBoth();
            assertEquals("NO_ACTIVE_TOKENS", logs.stage("EARLY_EVENT_FCM_REQUEST").path("resultCode").asText());
            assertTrue(logs.events().stream().noneMatch(log -> "EARLY_EVENT_TO_FCM".equals(log.path("stage").asText())));
        }
        finalResponse.respond(event(false), RiskLevel.HIGH);
        verify(notifications).sendSecurityAlert(any(), eq(3), eq("AUDIO"), eq("event"));
        verifyNoInteractions(sounds); verify(delivery, times(1)).sendSiren();
    }

    @Test void independentlyScheduledSoundRunsWhileFcmIsBlocked() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var siren = new CountDownLatch(1);
        when(delivery.sendPush(any())).thenAnswer(invocation -> { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return "REQUEST_ACCEPTED"; });
        doAnswer(invocation -> { siren.countDown(); return null; }).when(delivery).sendSiren();
        receive.receive(early(false, "one"));
        var fcm = executor(); var sound = executor();
        try {
            var kick = new EarlyWarningWorkers.EarlyWarningKick(dispatch, fcm, sound);
            kick.request(); assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(siren.await(5, TimeUnit.SECONDS));
        } finally { release.countDown(); fcm.shutdown(); sound.shutdown(); }
    }

    ThreadPoolTaskExecutor executor() {
        var pool = new ThreadPoolTaskExecutor(); pool.setCorePoolSize(1); pool.setMaxPoolSize(1);
        pool.setWaitForTasksToCompleteOnShutdown(true); pool.setAwaitTerminationSeconds(10); pool.initialize(); return pool;
    }

    @Test void invalidBindingAndChangedPayloadNeverScheduleAdditionalResponses() {
        var wrong = new VisionEarlyEvent("audio-session", "other", "event", VisionTriggerType.AUDIO, "one", 1, 100, 114, null, "2026-11-04T02:00:00Z");
        assertThrows(RuntimeException.class, () -> receive.receive(wrong));
        assertTrue(ledger.pending(FCM, 10).isEmpty()); verifyNoInteractions(delivery);
        receive.receive(early(false, "one")); sendBoth();
        var changed = new VisionEarlyEvent("audio-session", "audio-trigger", "event", VisionTriggerType.AUDIO, "one", 2, 100, 114, "3.8", "2026-11-04T02:00:00Z");
        assertThrows(RuntimeException.class, () -> receive.receive(changed)); sendBoth();
        verify(delivery, times(1)).sendSiren();
    }

    @Test void simultaneousFinalAndEarlyResponsesNeverDispatchTheSameChannelTwice() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var start = new CyclicBarrier(4);
            List<Callable<Void>> tasks = List.of(
                    () -> { start.await(); receive.receive(early(false, "one")); sendBoth(); return null; },
                    () -> { start.await(); finalResponse.respond(event(false), RiskLevel.HIGH); return null; },
                    () -> { start.await(); receive.receive(early(false, "one")); sendBoth(); return null; },
                    () -> { start.await(); finalResponse.respond(event(false), RiskLevel.HIGH); return null; });
            for (var result : pool.invokeAll(tasks, 20, TimeUnit.SECONDS)) result.get();
            sendBoth();
            long earlyPush = mockingDetails(delivery).getInvocations().stream().filter(i -> i.getMethod().getName().equals("sendPush")).count();
            long earlySiren = mockingDetails(delivery).getInvocations().stream().filter(i -> i.getMethod().getName().equals("sendSiren")).count();
            assertEquals(1, earlyPush + mockingDetails(notifications).getInvocations().size());
            assertEquals(1, earlySiren + mockingDetails(sounds).getInvocations().size());
        } finally { pool.shutdownNow(); }
    }

    @Test void migrationDoesNotReplayHistoricalStoreOnlyObservationsButAllowsNormalAgentResponse() throws Exception {
        context.getBean(VisionEarlyEventRepository.class).insert(early(false, "old"));
        jdbc.execute("DROP TABLE security_response_ledger");
        migrate("V12__create_security_response_ledger.sql");
        dispatch.recover(); receive.receive(early(false, "old")); sendBoth();
        verifyNoInteractions(delivery);
        finalResponse.respond(event(false), RiskLevel.MEDIUM);
        verify(notifications).sendSecurityAlert(any(), eq(2), eq("AUDIO"), eq("event"));
        verify(sounds).play(PlaySoundType.WARNING);
        receive.receive(early(false, "new-instance")); sendBoth();
        verifyNoInteractions(delivery);
    }

    @Test void preparedClaimSurvivesRestartWithoutReissuingAnUnconfirmedExternalRequest() {
        receive.receive(early(false, "one"));
        assertTrue(ledger.claimEarly("AUDIO:audio-trigger", FCM).isPresent());
        new DispatchEarlyWarningService(ledger, delivery).recover(); sendBoth();
        assertEquals("CALLING", state("fcm"));
        verify(delivery, never()).sendPush(any()); verify(delivery).sendSiren();
    }

    @Test void asynchronousElapsedTimeUsesOriginalServerHttpReceiptInsteadOfVisionEmissionOrReplayTime() {
        java.time.Instant received;
        try (var scope = com.omniguardy.backend.global.performance.PerformanceLog.request("early-http")) {
            received = com.omniguardy.backend.global.performance.PerformanceLog.requestReceivedAt();
            receive.receive(early(false, "one"));
        }
        var stored = jdbc.queryForObject("SELECT received_at FROM vision_early_events", LocalDateTime.class).toInstant(ZoneOffset.UTC);
        assertTrue(Math.abs(java.time.Duration.between(received, stored).toNanos()) < 1_000);
        receive.receive(early(false, "one"));
        assertEquals(stored, jdbc.queryForObject("SELECT received_at FROM vision_early_events", LocalDateTime.class).toInstant(ZoneOffset.UTC));
        assertNull(com.omniguardy.backend.global.performance.PerformanceLog.requestReceivedAt());
    }

    @Test void springManagedWorkersCanStartAndDispatchWithoutAnExistingVisionWorkerScheduler() throws Exception {
        receive.receive(early(false, "one"));
        var sent = new CountDownLatch(2);
        when(delivery.sendPush(any())).thenAnswer(i -> { sent.countDown(); return "REQUEST_ACCEPTED"; });
        doAnswer(i -> { sent.countDown(); return null; }).when(delivery).sendSiren();
        try (var workers = new AnnotationConfigApplicationContext()) {
            workers.setParent(context); workers.register(Scheduling.class); workers.refresh();
            workers.getBean(EarlyWarningWorkers.EarlyWarningKick.class).request();
            assertTrue(sent.await(5, TimeUnit.SECONDS));
        }
    }

    @Test void saturatedBoundedQueuesLeavePendingRowsForTheAutomaticRecoveryScheduler() throws Exception {
        var release = new CountDownLatch(1);
        var occupied = new CountDownLatch(2);
        var sent = new CountDownLatch(2);
        when(delivery.sendPush(any())).thenAnswer(i -> { sent.countDown(); return "REQUEST_ACCEPTED"; });
        doAnswer(i -> { sent.countDown(); return null; }).when(delivery).sendSiren();
        try (var workers = new AnnotationConfigApplicationContext()) {
            workers.setParent(context);
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(workers,
                    "omniguardy.early-warning.recovery-delay-ms=50");
            workers.register(Scheduling.class); workers.refresh();
            var fcm = workers.getBean("earlyWarningFcmExecutor", ThreadPoolTaskExecutor.class);
            var sound = workers.getBean("earlyWarningSoundExecutor", ThreadPoolTaskExecutor.class);
            Runnable block = () -> {
                occupied.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("worker release timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            };
            try {
                fcm.execute(block); sound.execute(block);
                assertTrue(occupied.await(5, TimeUnit.SECONDS));
                // Fill each queue using the same path as a callback, then reject another kick.
                workers.getBean(EarlyWarningWorkers.EarlyWarningKick.class).request();
                assertEquals(1, fcm.getQueueSize()); assertEquals(1, sound.getQueueSize());
                context.getBean(VisionEarlyEventRepository.class).insert(early(false, "queue-full"));
                dispatch.enqueue("audio-session");
                assertDoesNotThrow(workers.getBean(EarlyWarningWorkers.EarlyWarningKick.class)::request);
                assertEquals("PENDING", state("fcm")); assertEquals("PENDING", state("sound"));
                verifyNoInteractions(delivery);
                // Remove the queued kicks: only a new @Scheduled recovery tick can submit delivery.
                fcm.getThreadPoolExecutor().getQueue().clear();
                sound.getThreadPoolExecutor().getQueue().clear();
            } finally { release.countDown(); }
            assertTrue(sent.await(5, TimeUnit.SECONDS));
        }
        assertEquals("SUCCEEDED", state("fcm")); assertEquals("SUCCEEDED", state("sound"));
        verify(delivery, times(1)).sendPush(any()); verify(delivery, times(1)).sendSiren();
    }

    @Test void scheduledRecoveryFindsCommittedObservationWithoutAnyCallbackKick() throws Exception {
        context.getBean(VisionEarlyEventRepository.class).insert(early(false, "crash-after-store"));
        var sent = new CountDownLatch(2);
        when(delivery.sendPush(any())).thenAnswer(i -> { sent.countDown(); return "REQUEST_ACCEPTED"; });
        doAnswer(i -> { sent.countDown(); return null; }).when(delivery).sendSiren();
        try (var workers = new AnnotationConfigApplicationContext()) {
            workers.setParent(context);
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(workers,
                    "omniguardy.early-warning.recovery-delay-ms=50");
            workers.register(Scheduling.class); workers.refresh();
            assertTrue(sent.await(5, TimeUnit.SECONDS));
        }
        assertEquals("SUCCEEDED", state("fcm")); assertEquals("SUCCEEDED", state("sound"));
    }

    @Test void failedReservationRollsBackBothChannelsButKeepsTheCommittedObservationRecoverable() {
        jdbc.execute("ALTER TABLE security_response_ledger ADD CONSTRAINT test_fail_sound CHECK (sound_state<>'PENDING')");
        assertThrows(RuntimeException.class, () -> receive.receive(early(false, "rollback")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM security_response_ledger", Integer.class));
        jdbc.execute("ALTER TABLE security_response_ledger DROP CONSTRAINT test_fail_sound");
        dispatch.recover(); sendBoth();
        verify(delivery).sendPush(any()); verify(delivery).sendSiren();
    }

    @Test void providerCallsRunAfterTheCallingReservationCommitsAndOutsideTheLedgerTransaction() {
        when(delivery.sendPush(any())).thenAnswer(i -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("CALLING", state("fcm"));
            return "REQUEST_ACCEPTED";
        });
        doAnswer(i -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("CALLING", state("sound")); return null;
        }).when(delivery).sendSiren();
        receive.receive(early(false, "transaction")); sendBoth();
        assertEquals("SUCCEEDED", state("fcm")); assertEquals("SUCCEEDED", state("sound"));
    }

    @Test void keypadWithoutRecipientKeepsSirenAndAllowsFinalPushAfterEventLinking() {
        when(delivery.sendPush(any())).thenReturn("NO_RECIPIENT");
        receive.receive(early(true, "no-owner")); sendBoth();
        assertEquals("SKIPPED", state("fcm")); assertEquals("SUCCEEDED", state("sound"));
        jdbc.update("INSERT INTO security_events VALUES ('keypad-event','keypad-trigger','KEYPAD')");
        finalResponse.respond(event(true), RiskLevel.CRITICAL);
        verify(notifications).sendSecurityAlert(any(), eq(3), eq("KEYPAD"), eq("keypad-event"));
        verifyNoInteractions(sounds);
        dispatch.recover(); receive.receive(early(true, "no-owner")); sendBoth();
        verify(delivery, times(1)).sendPush(any()); verify(delivery, times(1)).sendSiren();
        assertEquals("keypad-event", jdbc.queryForObject("SELECT event_id FROM security_response_ledger", String.class));
    }

    @Test void finalResponseSuppressedWhileCallingIsNotAutomaticallyReplayedIfEarlyPushIsSkippedLater() {
        receive.receive(early(true, "owner-link-race"));
        var claim = ledger.claimEarly("KEYPAD:keypad-trigger", FCM).orElseThrow();
        assertNull(claim.binding().eventId());
        jdbc.update("INSERT INTO security_events VALUES ('keypad-event','keypad-trigger','KEYPAD')");
        finalResponse.respond(event(true), RiskLevel.HIGH);
        ledger.finished(claim, "SKIPPED");
        dispatch.recover(); sendBoth();
        assertEquals("SKIPPED", state("fcm"));
        assertEquals("keypad-event", jdbc.queryForObject("SELECT event_id FROM security_response_ledger", String.class));
        verifyNoInteractions(notifications, sounds);
        verify(delivery, never()).sendPush(any()); verify(delivery).sendSiren();
    }

    @Test void eventCreatedBeforeKeypadClaimIsLinkedAndAmbiguousEventsAreNeverArbitrarilySelected() {
        receive.receive(early(true, "linked"));
        jdbc.update("INSERT INTO security_events VALUES ('keypad-event','keypad-trigger','KEYPAD')");
        var claim = ledger.claimEarly("KEYPAD:keypad-trigger", FCM).orElseThrow();
        assertEquals("keypad-event", claim.binding().eventId());
        ledger.finished(claim, "SKIPPED");
        jdbc.update("UPDATE security_response_ledger SET event_id=NULL");
        jdbc.update("INSERT INTO security_events VALUES ('second-event','keypad-trigger','KEYPAD')");
        dispatch.recover();
        assertNull(jdbc.queryForObject("SELECT event_id FROM security_response_ledger", String.class));
        assertNull(ledger.claimEarly("KEYPAD:keypad-trigger", SOUND).orElseThrow().binding().eventId());
    }

    @Test void keypadClaimDoesNotLinkACaseInsensitiveLegacyEventMatch() {
        jdbc.execute("ALTER TABLE security_events ALTER COLUMN trigger_id VARCHAR_IGNORECASE(64)");
        receive.receive(early(true, "case-sensitive"));
        jdbc.update("INSERT INTO security_events VALUES ('wrong-case-event','KEYPAD-TRIGGER','KEYPAD')");
        assertNull(ledger.claimEarly("KEYPAD:keypad-trigger", FCM).orElseThrow().binding().eventId());
        jdbc.update("INSERT INTO security_events VALUES ('keypad-event','keypad-trigger','KEYPAD')");
        assertEquals("keypad-event", ledger.claimEarly("KEYPAD:keypad-trigger", SOUND).orElseThrow().binding().eventId());
    }

    @Test void migrationGroupsHistoricalInstancesPreservesCaseSensitiveTriggersAndNeverReplaysSiren() throws Exception {
        var repository = context.getBean(VisionEarlyEventRepository.class);
        repository.insert(early(false, "old-one")); repository.insert(early(false, "old-two"));
        repository.insert(early(true, "old-keypad"));
        String trigger = "K".repeat(64);
        for (String id : List.of(trigger, trigger.toLowerCase(Locale.ROOT))) {
            jdbc.update("INSERT INTO keypad_camera_triggers VALUES (?,?)", id, LocalDateTime.now(ZoneOffset.UTC));
            context.getBean(VisionSessionRepository.class).insert(VisionSession.register(id, VisionTriggerType.KEYPAD, null, id));
            repository.insert(new VisionEarlyEvent(id, id, null, VisionTriggerType.KEYPAD, "old", 1, 1, 2, null, "2026-11-04T02:00:00Z"));
        }
        jdbc.execute("DROP TABLE security_response_ledger"); migrate("V12__create_security_response_ledger.sql");
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM security_response_ledger", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM vision_early_events", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM security_response_ledger WHERE early_received=TRUE AND fcm_state='SKIPPED' AND sound_state='SKIPPED'", Integer.class));
        assertEquals(jdbc.queryForObject("SELECT MIN(received_at) FROM vision_early_events WHERE trigger_type='AUDIO'", LocalDateTime.class),
                jdbc.queryForObject("SELECT received_at FROM security_response_ledger WHERE trigger_type='AUDIO'", LocalDateTime.class));
        dispatch.recover(); sendBoth(); verifyNoInteractions(delivery);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM security_events", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM vision_sessions WHERE status='REGISTERED'", Integer.class));
    }

    @Configuration @org.springframework.scheduling.annotation.EnableScheduling
    @Import(EarlyWarningWorkers.class)
    static class Scheduling {}

    @Configuration @EnableTransactionManagement
    @Import({JdbcSecurityResponseLedger.class, DispatchEarlyWarningService.class, JdbcVisionSessionRepository.class,
            JdbcVisionEarlyEventRepository.class, JdbcKeypadTriggerAdapter.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean EarlyWarningDeliveryPort delivery() { return mock(EarlyWarningDeliveryPort.class); }
        @Bean VisionAudioTriggerPort audio() { return mock(VisionAudioTriggerPort.class); }
    }
}
