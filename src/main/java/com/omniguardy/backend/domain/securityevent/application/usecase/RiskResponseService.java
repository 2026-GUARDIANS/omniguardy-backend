package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.tts.application.port.out.TtsDeliveryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskResponseService {

    private static final int MEDIUM_NOTIFICATION_LEVEL = 2;
    private static final int HIGH_NOTIFICATION_LEVEL = 3;
    private static final String HIGH_RISK_WARNING =
            "위험 상황이 감지되었습니다. 즉시 주변 상황을 확인해 주세요.";

    private final NotificationService notificationService;
    private final TtsDeliveryPort ttsDeliveryPort;

    public void respond(SecurityEvent event, RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW -> {
                // LOW events are recorded without an external response.
            }
            case MEDIUM -> sendPushIfRecipientExists(event, MEDIUM_NOTIFICATION_LEVEL);
            case HIGH, CRITICAL -> {
                sendPushIfRecipientExists(event, HIGH_NOTIFICATION_LEVEL);
                publishHighRiskWarning(event);
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
            notificationService.sendSecurityAlert(event.getUser(), notificationLevel, eventType);
        } catch (RuntimeException exception) {
            log.error("Risk push failed: eventId={}, riskLevel={}",
                    event.getEventId(), event.getRiskLevel(), exception);
        }
    }

    private void publishHighRiskWarning(SecurityEvent event) {
        try {
            ttsDeliveryPort.synthesizeAndPublish(HIGH_RISK_WARNING);
        } catch (RuntimeException exception) {
            log.error("High-risk TTS warning failed: eventId={}", event.getEventId(), exception);
        }
    }
}
