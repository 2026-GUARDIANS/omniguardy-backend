package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.user.domain.model.User;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class RiskResponseServiceTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final PlaySoundUseCase playSoundUseCase = mock(PlaySoundUseCase.class);
    private final RiskResponseService service = new RiskResponseService(notificationService, playSoundUseCase);

    @Test
    void lowOnlyRecordsTheAssessment() {
        service.respond(event(mock(User.class)), RiskLevel.LOW);

        verifyNoInteractions(notificationService, playSoundUseCase);
    }

    @Test
    void mediumSendsOnlyPushNotification() {
        User user = mock(User.class);

        service.respond(event(user), RiskLevel.MEDIUM);

        verify(notificationService).sendSecurityAlert(user, 2, "AUDIO");
        verifyNoInteractions(playSoundUseCase);
    }

    @Test
    void mediumWithoutRecipientSkipsPushWithoutFailing() {
        service.respond(event(null), RiskLevel.MEDIUM);

        verifyNoInteractions(notificationService, playSoundUseCase);
    }

    @Test
    void highSendsPushAndPlaysWarning() {
        User user = mock(User.class);

        service.respond(event(user), RiskLevel.HIGH);

        verify(notificationService).sendSecurityAlert(user, 3, "AUDIO");
        verify(playSoundUseCase).play(PlaySoundType.WARNING);
    }

    @Test
    void highWithoutRecipientStillPlaysWarning() {
        service.respond(event(null), RiskLevel.HIGH);

        verifyNoInteractions(notificationService);
        verify(playSoundUseCase).play(PlaySoundType.WARNING);
    }

    @Test
    void pushFailureDoesNotPreventHighRiskWarning() {
        User user = mock(User.class);
        doThrow(new IllegalStateException("FCM unavailable"))
                .when(notificationService).sendSecurityAlert(user, 3, "AUDIO");

        service.respond(event(user), RiskLevel.HIGH);

        verify(playSoundUseCase).play(PlaySoundType.WARNING);
    }

    @Test
    void soundFailureDoesNotEscapeResponseHandling() {
        doThrow(new IllegalStateException("Sound unavailable"))
                .when(playSoundUseCase).play(any(PlaySoundType.class));

        service.respond(event(null), RiskLevel.HIGH);

        verify(playSoundUseCase).play(PlaySoundType.WARNING);
    }

    @Test
    void criticalSendsPushAndPlaysSiren() {
        User user = mock(User.class);

        service.respond(event(user), RiskLevel.CRITICAL);

        verify(notificationService).sendSecurityAlert(user, 3, "AUDIO");
        verify(playSoundUseCase).play(PlaySoundType.SIREN);
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
