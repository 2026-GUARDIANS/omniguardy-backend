package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.omniguardy.backend.global.performance.PerformanceLog;
import com.omniguardy.backend.domain.securityevent.application.port.out.SecurityResponseLedger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskResponseService {

    private static final int MEDIUM_NOTIFICATION_LEVEL = 2;
    private static final int HIGH_NOTIFICATION_LEVEL = 3;

    private final NotificationService notificationService;
    private final PlaySoundUseCase playSoundUseCase;
    private SecurityResponseLedger responseLedger;

    @org.springframework.beans.factory.annotation.Autowired
    public void setResponseLedger(SecurityResponseLedger responseLedger) { this.responseLedger = responseLedger; }

    public void respond(SecurityEvent event, RiskLevel riskLevel) {
        try (var scope = PerformanceLog.scope(event.getEventId(), event.getTriggerId(), null,
                event.getTriggerType() == null ? null : event.getTriggerType().name(), null)) {
            dispatch(event, riskLevel);
        }
    }

    private void dispatch(SecurityEvent event, RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW -> {
                // LOW events are recorded without an external response.
            }
            case MEDIUM -> {
                sendPushIfRecipientExists(event, MEDIUM_NOTIFICATION_LEVEL);
                playRiskSound(event, riskLevel);
            }
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

        SecurityResponseLedger.Claim claim = null;
        try {
            if (responseLedger != null && event.getTriggerId() != null && event.getTriggerType() != null) {
                var reserved = responseLedger.claimFinal(binding(event), SecurityResponseLedger.Channel.FCM, notificationLevel);
                if (reserved.isEmpty()) return;
                claim = reserved.get();
            }
            String eventType = event.getTriggerType() == null
                    ? "SECURITY_EVENT"
                    : event.getTriggerType().name();
            notificationService.sendSecurityAlert(event.getUser(), notificationLevel, eventType, event.getEventId());
            if (claim != null) responseLedger.finished(claim, "SUCCEEDED");
        } catch (RuntimeException exception) {
            uncertain(claim);
            log.error("Risk push failed: eventId={}, riskLevel={}",
                    event.getEventId(), event.getRiskLevel(), exception);
        }
    }

    private void playRiskSound(SecurityEvent event, RiskLevel riskLevel) {
        SecurityResponseLedger.Claim claim = null;
        try {
            if (responseLedger != null && event.getTriggerId() != null && event.getTriggerType() != null) {
                var reserved = responseLedger.claimFinal(binding(event), SecurityResponseLedger.Channel.SOUND,
                        riskLevel == RiskLevel.MEDIUM ? 2 : 3);
                if (reserved.isEmpty()) return;
                claim = reserved.get();
            }
            PlaySoundType soundType = riskLevel == RiskLevel.MEDIUM
                    ? PlaySoundType.WARNING
                    : PlaySoundType.SIREN;
            playSoundUseCase.play(soundType);
            if (claim != null) responseLedger.finished(claim, "SUCCEEDED");
        } catch (RuntimeException exception) {
            uncertain(claim);
            log.error("Risk sound playback failed: eventId={}, riskLevel={}",
                    event.getEventId(), riskLevel, exception);
        }
    }

    private SecurityResponseLedger.Binding binding(SecurityEvent event) {
        return new SecurityResponseLedger.Binding(event.getTriggerType().name() + ":" + event.getTriggerId(),
                event.getEventId(), event.getTriggerId(), null, event.getTriggerType().name(), null);
    }

    private void uncertain(SecurityResponseLedger.Claim claim) {
        if (claim == null) return;
        try { responseLedger.finished(claim, "UNCERTAIN"); }
        catch (RuntimeException ignored) { /* CALLING remains fenced if the status write fails. */ }
    }
}
