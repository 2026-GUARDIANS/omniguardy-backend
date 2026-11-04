package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.application.usecase.ReceiveVisionEarlyEventService;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionEarlyEventException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.*;
import java.util.Optional;
import static com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisionEarlyEventServiceTest {
    VisionSessionRepository sessions = mock(VisionSessionRepository.class);
    KeypadTriggerPort keypad = mock(KeypadTriggerPort.class);
    VisionAudioTriggerPort audio = mock(VisionAudioTriggerPort.class);
    VisionEarlyEventRepository events = mock(VisionEarlyEventRepository.class);
    VisionEarlyWarningPort warnings = mock(VisionEarlyWarningPort.class);
    ReceiveVisionEarlyEventService service = new ReceiveVisionEarlyEventService(sessions, keypad, audio, events, warnings);

    static VisionEarlyEvent event(VisionTriggerType type, String trigger, String securityEventId, String instance) {
        return new VisionEarlyEvent("session", trigger, securityEventId, type, instance, 1, 114, 114,
                "3.800000", "2026-11-02T14:44:19.167+09:00");
    }

    @BeforeEach void setup() {
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", KEYPAD, null, "trigger")));
        when(keypad.exists("trigger")).thenReturn(true);
    }

    @Test void keypadStoresOnlyAndLeavesSessionUnchanged() {
        var event = event(KEYPAD, "trigger", null, "signal-001");
        service.receive(event);
        verify(events).insert(event);
        verify(warnings).request("session");
        verify(sessions, never()).insert(any());
        verify(keypad, never()).record(any(), any());
        verifyNoInteractions(audio);
    }

    @Test void audioMatchesBothExistingEventAndItsIssuedTrigger() {
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", AUDIO, "event", null)));
        when(audio.findIssuedTriggerId("event")).thenReturn(Optional.of("audio-trigger"));
        var event = event(AUDIO, "audio-trigger", "event", "signal-001");
        service.receive(event);
        verify(events).insert(event);
        verifyNoInteractions(keypad);
    }

    @Test void missingSessionAndMissingIssuedTriggersDoNotStore() {
        when(sessions.findBySessionId("session")).thenReturn(Optional.empty());
        assertCode("VISION_SESSION_NOT_FOUND", event(KEYPAD, "trigger", null, "one"));
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", KEYPAD, null, "trigger")));
        when(keypad.exists("trigger")).thenReturn(false);
        assertCode("VISION_TRIGGER_NOT_FOUND", event(KEYPAD, "trigger", null, "one"));
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", AUDIO, "event", null)));
        assertCode("VISION_TRIGGER_NOT_FOUND", event(AUDIO, "trigger", "event", "one"));
        verifyNoInteractions(events);
    }

    @Test void rejectsEveryCrossBindingWithoutStorage() {
        for (var event : new VisionEarlyEvent[]{event(KEYPAD, "other", null, "one"),
                event(KEYPAD, "trigger", "event", "one"), event(AUDIO, "trigger", "event", "one")}) {
            assertCode("VISION_CALLBACK_BINDING_MISMATCH", event);
        }
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", AUDIO, "event", null)));
        when(audio.findIssuedTriggerId("event")).thenReturn(Optional.of("audio-trigger"));
        for (var event : new VisionEarlyEvent[]{event(AUDIO, "audio-trigger", null, "one"),
                event(AUDIO, "audio-trigger", "wrong-event", "one"), event(AUDIO, "wrong-trigger", "event", "one"),
                event(KEYPAD, "trigger", null, "one")}) {
            assertCode("VISION_CALLBACK_BINDING_MISMATCH", event);
        }
        verifyNoInteractions(events);
    }

    @Test void identicalRetrySucceedsButChangedPayloadConflicts() {
        var event = event(KEYPAD, "trigger", null, "one");
        doThrow(new DuplicateVisionEarlyEventException(new RuntimeException())).when(events).insert(any());
        when(events.find("session", "one")).thenReturn(Optional.of(event));
        assertDoesNotThrow(() -> service.receive(event));
        verify(warnings).request("session");
        var changed = new VisionEarlyEvent("session", "trigger", null, KEYPAD, "one", 2, 114, 114,
                "3.800000", event.emittedAt());
        assertCode("VISION_EARLY_EVENT_CONFLICT", changed);
        verify(warnings, times(1)).request("session");
        when(events.find("session", "one")).thenReturn(Optional.empty());
        assertCode("VISION_EARLY_EVENT_CONFLICT", event);
    }

    @Test void terminalSessionStillAcceptsDelayedRetryAndDistinctInstancesWithSameSequence() {
        when(sessions.findBySessionId("session")).thenReturn(Optional.of(VisionSession.register("session", KEYPAD, null, "trigger").complete()));
        service.receive(event(KEYPAD, "trigger", null, "one"));
        service.receive(event(KEYPAD, "trigger", null, "two"));
        verify(events, times(2)).insert(any());
    }

    @Test void storageFailuresPropagateForRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(events).insert(any());
        assertThrows(IllegalStateException.class, () -> service.receive(event(KEYPAD, "trigger", null, "one")));
    }

    void assertCode(String code, VisionEarlyEvent event) {
        assertEquals(code, assertThrows(BusinessException.class, () -> service.receive(event)).getErrorCode().getCode());
    }
}
