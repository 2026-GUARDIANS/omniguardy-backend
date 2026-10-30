package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskResponseService {

    private static final int MEDIUM_NOTIFICATION_LEVEL = 2;
    private static final int HIGH_NOTIFICATION_LEVEL = 3;

    private final NotificationService notificationService;
    private final PlaySoundUseCase playSoundUseCase;

    public void respond(SecurityEvent event, RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW -> {
                // LOW events are recorded without an external response.
            }
            case MEDIUM -> sendPushIfRecipientExists(event, MEDIUM_NOTIFICATION_LEVEL);
            case HIGH, CRITICAL -> {
                sendPushIfRecipientExists(event, HIGH_NOTIFICATION_LEVEL);
                playRiskSound(event, riskLevel);
            }
        }
    }

    private void sendPushIfRecipientExists(SecurityEvent event, int notificationLevel) {
        if (event.getUser() == null) {
            log.warn("Risk push skipped because no user is linked: eventId={}, riskLevel={}",
                    event.getEventId(), event.getRiskLevel());
            return;
        }

        try {
            String eventType = event.getTriggerType() == null
                    ? "SECURITY_EVENT"
                    : event.getTriggerType().name();
            notificationService.sendSecurityAlert(event.getUser(), notificationLevel, eventType, event.getEventId());
        } catch (RuntimeException exception) {
            log.error("Risk push failed: eventId={}, riskLevel={}",
                    event.getEventId(), event.getRiskLevel(), exception);
        }
    }

    private void playRiskSound(SecurityEvent event, RiskLevel riskLevel) {
        try {
            PlaySoundType soundType = riskLevel == RiskLevel.CRITICAL
                    ? PlaySoundType.SIREN
                    : PlaySoundType.WARNING;
            playSoundUseCase.play(soundType);
        } catch (RuntimeException exception) {
            log.error("Risk sound playback failed: eventId={}, riskLevel={}",
                    event.getEventId(), riskLevel, exception);
        }
    }
}
