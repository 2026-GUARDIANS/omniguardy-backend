package com.omniguardy.backend.domain.notification.application.usecase;

import com.omniguardy.backend.domain.notification.application.port.out.PushNotificationPort;
import com.omniguardy.backend.domain.notification.domain.model.DeviceToken;
import com.omniguardy.backend.domain.notification.domain.model.Notification;
import com.omniguardy.backend.domain.notification.domain.model.NotificationType;
import com.omniguardy.backend.domain.notification.domain.repository.DeviceTokenRepository;
import com.omniguardy.backend.domain.notification.domain.repository.NotificationRepository;
import com.omniguardy.backend.domain.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final DeviceTokenRepository tokens = mock(DeviceTokenRepository.class);
    private final PushNotificationPort push = mock(PushNotificationPort.class);
    private final NotificationService service = new NotificationService(notifications, tokens, push);
    private final User owner = mock(User.class);

    @ParameterizedTest
    @CsvSource({"AUDIO, 2", "AUDIO, 3", "KEYPAD, 3"})
    void sendsPublicEventIdAndExistingDataToEveryActiveOwnerToken(String eventType, int riskLevel) {
        when(tokens.findAllByUserAndActiveTrue(owner)).thenReturn(List.of(
                DeviceToken.builder().user(owner).token("android-1").active(true).build(),
                DeviceToken.builder().user(owner).token("android-2").active(true).build()));

        service.sendSecurityAlert(owner, riskLevel, eventType, "evt-video-20261029-001");

        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        Notification notification = saved.getValue();
        assertSame(owner, notification.getUser());
        assertEquals(NotificationType.SECURITY_EVENT, notification.getType());
        assertEquals(riskLevel, notification.getRiskLevel());
        assertFalse(notification.isRead());
        for (String token : List.of("android-1", "android-2")) {
            verify(push).send(token, notification.getTitle(), notification.getMessage(),
                    "SECURITY_EVENT", String.valueOf(riskLevel), "evt-video-20261029-001");
        }
        verify(tokens).findAllByUserAndActiveTrue(owner);
        verifyNoMoreInteractions(tokens, push);
    }

    @Test
    void keepsNotificationRecordWhenOwnerHasNoActiveDeviceTokens() {
        when(tokens.findAllByUserAndActiveTrue(owner)).thenReturn(List.of());

        service.sendSecurityAlert(owner, 3, "AUDIO", "evt-public");

        verify(notifications).save(any(Notification.class));
        verifyNoInteractions(push);
    }

    @Test
    void preservesExistingTestNotificationWithoutAssociatedEvent() {
        when(tokens.findAllByUserAndActiveTrue(owner)).thenReturn(List.of(
                DeviceToken.builder().user(owner).token("android-test").active(true).build()));

        service.sendSecurityAlert(owner, 2, "PERSON_DETECTED");

        verify(push).send(eq("android-test"), anyString(), anyString(), eq("SECURITY_EVENT"), eq("2"), isNull());
    }

    @Test void earlyAlertReusesPushPortAndPublicEventIdAndClearlyStatesFinalAnalysisIsPending() {
        when(tokens.findAllByUserAndActiveTrue(owner)).thenReturn(List.of(
                DeviceToken.builder().user(owner).token("early-test-token").active(true).build()));
        assertTrue(service.sendEarlySecurityAlert(owner, "evt-early", "AUDIO"));
        var notification = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(notification.capture());
        assertTrue(notification.getValue().getMessage().contains("최종 분석은 진행 중"));
        verify(push).send(eq("early-test-token"), eq(notification.getValue().getTitle()),
                eq(notification.getValue().getMessage()), eq("SECURITY_EVENT"), eq("3"), eq("evt-early"));
    }

    @Test void earlyAlertWithoutTokensDoesNotClaimProviderDelivery() {
        when(tokens.findAllByUserAndActiveTrue(owner)).thenReturn(List.of());
        assertFalse(service.sendEarlySecurityAlert(owner, null, "KEYPAD"));
        verifyNoInteractions(push);
    }
}
