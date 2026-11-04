package com.omniguardy.backend.domain.securityevent;

import com.omniguardy.backend.domain.securityevent.application.port.out.SecurityResponseLedger.Binding;
import com.omniguardy.backend.domain.securityevent.infrastructure.response.ExistingEarlyWarningDeliveryAdapter;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.sound.application.port.out.SoundCommandPort;
import com.omniguardy.backend.domain.sound.domain.model.SoundType;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExistingEarlyWarningDeliveryAdapterTest {
    SecurityEventRepository events = mock(SecurityEventRepository.class);
    DemoUserResolver keypadOwner = mock(DemoUserResolver.class);
    NotificationService notifications = mock(NotificationService.class);
    SoundCommandPort sound = mock(SoundCommandPort.class);
    ExistingEarlyWarningDeliveryAdapter adapter = new ExistingEarlyWarningDeliveryAdapter(events, keypadOwner, notifications, sound);
    User user = mock(User.class);
    Binding binding(String eventId, String type) { return new Binding(type + ":trigger", eventId, "trigger", "session", type, null); }

    @Test void audioUsesTheExistingOwnerNotificationServiceAndSoundOutboundPort() {
        var event = SecurityEvent.builder().eventId("event").triggerId("trigger").triggerType(EventTriggerType.AUDIO).user(user).status("CAMERA_REQUESTED").build();
        when(events.findByEventId("event")).thenReturn(Optional.of(event));
        when(notifications.sendEarlySecurityAlert(user, "event", "AUDIO")).thenReturn(true);
        assertEquals("REQUEST_ACCEPTED", adapter.sendPush(binding("event", "AUDIO")));
        adapter.sendSiren(); verify(sound).send(SoundType.SIREN);
        verifyNoInteractions(keypadOwner); verify(events, never()).save(any());
        assertEquals("CAMERA_REQUESTED", event.getStatus()); assertNull(event.getRiskLevel());
    }

    @Test void keypadUsesExistingOwnerConventionWithoutManufacturingAnEventId() {
        when(keypadOwner.resolve()).thenReturn(Optional.of(user));
        when(notifications.sendEarlySecurityAlert(user, null, "KEYPAD")).thenReturn(true);
        assertEquals("REQUEST_ACCEPTED", adapter.sendPush(binding(null, "KEYPAD")));
        verifyNoInteractions(events);
    }

    @Test void missingOwnerAndMissingTokensAreExplicitSkips() {
        when(events.findByEventId("event")).thenReturn(Optional.empty());
        assertEquals("NO_RECIPIENT", adapter.sendPush(binding("event", "AUDIO")));
        when(keypadOwner.resolve()).thenReturn(Optional.of(user));
        assertEquals("NO_ACTIVE_TOKENS", adapter.sendPush(binding(null, "KEYPAD")));
    }

    @Test void eventBindingMismatchCannotSendFcm() {
        when(events.findByEventId("event")).thenReturn(Optional.of(SecurityEvent.builder().eventId("event")
                .triggerId("other").triggerType(EventTriggerType.AUDIO).user(user).build()));
        assertThrows(IllegalStateException.class, () -> adapter.sendPush(binding("event", "AUDIO")));
        verifyNoInteractions(notifications);
    }

    @Test void missingConfiguredKeypadOwnerIsSkippedBeforeAnyProviderCall() {
        for (var code : java.util.List.of(
                com.omniguardy.backend.domain.user.domain.error.UserErrorCode.DEMO_USER_ID_NOT_CONFIGURED,
                com.omniguardy.backend.domain.user.domain.error.UserErrorCode.USER_NOT_FOUND)) {
            doThrow(new com.omniguardy.backend.global.error.exception.BusinessException(code)).when(keypadOwner).resolve();
            assertEquals("NO_RECIPIENT", adapter.sendPush(binding(null, "KEYPAD")));
        }
        verifyNoInteractions(events, notifications, sound);
    }

    @Test void ownerDatabaseFailuresRemainUncertainRatherThanBeingMisclassifiedAsMissingRecipients() {
        when(keypadOwner.resolve()).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> adapter.sendPush(binding(null, "KEYPAD")));
        verifyNoInteractions(notifications);
    }

    @Test void linkedKeypadUsesTheValidatedEventOwnerInsteadOfResolvingTheDemoOwnerAgain() {
        when(events.findByEventId("event")).thenReturn(Optional.of(SecurityEvent.builder().eventId("event")
                .triggerId("trigger").triggerType(EventTriggerType.KEYPAD).user(user).build()));
        when(notifications.sendEarlySecurityAlert(user, "event", "KEYPAD")).thenReturn(true);
        assertEquals("REQUEST_ACCEPTED", adapter.sendPush(binding("event", "KEYPAD")));
        verifyNoInteractions(keypadOwner);
    }
}
