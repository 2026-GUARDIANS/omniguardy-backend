package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.VisionSessionService;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionSessionException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.*;
import java.time.OffsetDateTime;
import java.util.Optional;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisionSessionServiceTest {
    VisionSessionRepository sessions = mock(VisionSessionRepository.class);
    KeypadTriggerPort triggers = mock(KeypadTriggerPort.class);
    SecurityEventRepository events = mock(SecurityEventRepository.class);
    VisionSessionService service = new VisionSessionService(sessions, triggers, events);

    SecurityEvent audio() {
        return SecurityEvent.builder().eventId("event").triggerId("event").triggerType(EventTriggerType.AUDIO)
                .triggeredAt(OffsetDateTime.now()).status("CAMERA_REQUESTED").build();
    }

    @Test void registersAudioWithoutChangingSecurityEventOrInvokingOtherWork() {
        var event = audio();
        when(events.findByEventId("event")).thenReturn(Optional.of(event));
        var result = service.register("session", AUDIO, "event", null);
        assertTrue(result.created());
        assertEquals(VisionSessionStatus.REGISTERED, result.session().status());
        assertEquals("CAMERA_REQUESTED", event.getStatus());
        verify(sessions).insert(result.session());
        verify(events, never()).save(any());
        verifyNoInteractions(triggers);
    }

    @Test void registersKeypadWithoutCreatingSecurityEvent() {
        when(triggers.exists("trigger")).thenReturn(true);
        var result = service.register("session", KEYPAD, null, "trigger");
        assertNull(result.session().eventId());
        verifyNoInteractions(events);
    }

    @Test void rejectsMissingTriggersAndIneligibleAudio() {
        assertEquals(VISION_TRIGGER_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.register("s1", AUDIO, "missing", null)).getErrorCode());
        assertEquals(VISION_TRIGGER_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.register("s2", KEYPAD, null, "missing")).getErrorCode());
        for (String status : new String[]{"AUDIO_DETECTED", "COMPLETED", "FAILED"}) {
            var event = audio(); event.updateStatus(status);
            when(events.findByEventId("event")).thenReturn(Optional.of(event));
            assertEquals(INVALID_VISION_TRIGGER, assertThrows(BusinessException.class,
                    () -> service.register("s3", AUDIO, "event", null)).getErrorCode());
        }
        when(events.findByEventId("event")).thenReturn(Optional.of(SecurityEvent.builder()
                .triggerType(EventTriggerType.KEYPAD).status("CAMERA_REQUESTED").build()));
        assertEquals(INVALID_VISION_TRIGGER, assertThrows(BusinessException.class,
                () -> service.register("s4", AUDIO, "event", null)).getErrorCode());
        verify(sessions, never()).insert(any());
    }

    @Test void rejectsMissingOrMixedReferencesBeforePersistence() {
        assertThrows(BusinessException.class, () -> service.register("s", AUDIO, null, null));
        assertThrows(BusinessException.class, () -> service.register("s", AUDIO, "event", "trigger"));
        assertThrows(BusinessException.class, () -> service.register("s", KEYPAD, "event", "trigger"));
        assertThrows(BusinessException.class, () -> service.register("s", KEYPAD, null, " "));
        assertThrows(BusinessException.class, () -> service.register("../s", KEYPAD, null, "trigger"));
        assertThrows(BusinessException.class, () -> service.register("s".repeat(129), KEYPAD, null, "trigger"));
        verifyNoInteractions(sessions, triggers, events);
    }

    @Test void retryReturnsStoredStatusAndTimeEvenAfterTriggerStatusChanges() {
        var stored = VisionSession.register("s", AUDIO, "event", null).complete();
        when(sessions.findBySessionId("s")).thenReturn(Optional.of(stored));
        var replay = service.register("s", AUDIO, "event", null);
        assertFalse(replay.created()); assertSame(stored, replay.session());
        verifyNoInteractions(events, triggers);
        verify(sessions, never()).insert(any());
    }

    @Test void rejectsRebindingSessionAcrossTypesOrReferences() {
        when(sessions.findBySessionId("s")).thenReturn(Optional.of(VisionSession.register("s", AUDIO, "event", null)));
        assertEquals(VISION_SESSION_CONFLICT, assertThrows(BusinessException.class,
                () -> service.register("s", AUDIO, "other", null)).getErrorCode());
        assertEquals(VISION_SESSION_CONFLICT, assertThrows(BusinessException.class,
                () -> service.register("s", KEYPAD, null, "trigger")).getErrorCode());
        verifyNoInteractions(events, triggers);
    }

    @Test void recoversIdenticalConcurrentInsertAndRejectsOtherSessionForTrigger() {
        var stored = VisionSession.register("s", KEYPAD, null, "trigger");
        when(triggers.exists("trigger")).thenReturn(true);
        when(sessions.findBySessionId("s")).thenReturn(Optional.empty(), Optional.of(stored));
        doThrow(new DuplicateVisionSessionException(new RuntimeException())).when(sessions).insert(any());
        assertEquals(stored, service.register("s", KEYPAD, null, "trigger").session());
        assertEquals(VISION_SESSION_CONFLICT, assertThrows(BusinessException.class,
                () -> service.register("other", KEYPAD, null, "trigger")).getErrorCode());
    }

    @Test void queryValidatesIdAndReportsMissingSession() {
        assertEquals(VISION_SESSION_NOT_FOUND, assertThrows(BusinessException.class, () -> service.find("missing")).getErrorCode());
        assertThrows(BusinessException.class, () -> service.find(" "));
        var stored = VisionSession.register("s", KEYPAD, null, "trigger");
        when(sessions.findBySessionId("s")).thenReturn(Optional.of(stored));
        assertSame(stored, service.find("s"));
    }

    @Test void terminalTransitionsAreIdempotentAndCannotBeReversed() {
        var registered = VisionSession.register("s", KEYPAD, null, "trigger");
        var completed = registered.complete(); var failed = registered.fail();
        assertSame(completed, completed.complete()); assertSame(failed, failed.fail());
        assertEquals(registered.registeredAt(), completed.registeredAt());
        assertEquals(INVALID_VISION_SESSION_STATUS, assertThrows(BusinessException.class, completed::fail).getErrorCode());
        assertThrows(BusinessException.class, failed::complete);
    }
}
