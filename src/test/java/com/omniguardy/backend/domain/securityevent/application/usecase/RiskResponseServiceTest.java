package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.tts.application.port.out.TtsDeliveryPort;
import com.omniguardy.backend.domain.user.domain.model.User;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RiskResponseServiceTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final TtsDeliveryPort ttsDeliveryPort = mock(TtsDeliveryPort.class);
    private final RiskResponseService service = new RiskResponseService(notificationService, ttsDeliveryPort);

    @Test
    void lowOnlyRecordsTheAssessment() {
        service.respond(event(mock(User.class)), RiskLevel.LOW);

        verifyNoInteractions(notificationService, ttsDeliveryPort);
    }

    @Test
    void mediumSendsOnlyPushNotification() {
        User user = mock(User.class);

        service.respond(event(user), RiskLevel.MEDIUM);

        verify(notificationService).sendSecurityAlert(user, 2, "AUDIO");
        verifyNoInteractions(ttsDeliveryPort);
    }

    @Test
    void mediumWithoutRecipientSkipsPushWithoutFailing() {
        service.respond(event(null), RiskLevel.MEDIUM);

        verifyNoInteractions(notificationService, ttsDeliveryPort);
    }

    @Test
    void highSendsPushAndPublishesTtsWarning() {
        User user = mock(User.class);

        service.respond(event(user), RiskLevel.HIGH);

        verify(notificationService).sendSecurityAlert(user, 3, "AUDIO");
        verify(ttsDeliveryPort).synthesizeAndPublish(
                "위험 상황이 감지되었습니다. 즉시 주변 상황을 확인해 주세요.");
    }

    @Test
    void highWithoutRecipientStillPublishesTtsWarning() {
        service.respond(event(null), RiskLevel.HIGH);

        verifyNoInteractions(notificationService);
        verify(ttsDeliveryPort).synthesizeAndPublish(anyString());
    }

    @Test
    void pushFailureDoesNotPreventHighRiskTtsWarning() {
        User user = mock(User.class);
        doThrow(new IllegalStateException("FCM unavailable"))
                .when(notificationService).sendSecurityAlert(user, 3, "AUDIO");

        service.respond(event(user), RiskLevel.HIGH);

        verify(ttsDeliveryPort).synthesizeAndPublish(anyString());
    }

    @Test
    void ttsFailureDoesNotEscapeResponseHandling() {
        doThrow(new IllegalStateException("TTS unavailable"))
                .when(ttsDeliveryPort).synthesizeAndPublish(anyString());

        service.respond(event(null), RiskLevel.HIGH);

        verify(ttsDeliveryPort).synthesizeAndPublish(anyString());
    }

    private SecurityEvent event(User user) {
        return SecurityEvent.builder()
                .eventId("event-1")
                .triggerType(EventTriggerType.AUDIO)
                .user(user)
                .riskLevel("HIGH")
                .status("COMPLETED")
                .build();
    }
}
