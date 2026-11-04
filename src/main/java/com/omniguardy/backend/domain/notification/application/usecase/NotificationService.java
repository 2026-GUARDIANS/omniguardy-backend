package com.omniguardy.backend.domain.notification.application.usecase;

import com.omniguardy.backend.domain.notification.application.port.out.PushNotificationPort;
import com.omniguardy.backend.domain.notification.domain.model.DeviceToken;
import com.omniguardy.backend.domain.notification.domain.model.Notification;
import com.omniguardy.backend.domain.notification.domain.model.NotificationType;
import com.omniguardy.backend.domain.notification.domain.repository.DeviceTokenRepository;
import com.omniguardy.backend.domain.notification.domain.repository.NotificationRepository;
import com.omniguardy.backend.domain.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.omniguardy.backend.global.performance.PerformanceLog;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushNotificationPort pushNotificationPort;

    @Transactional
    public void sendSecurityAlert(User user, int riskLevel, String eventType) {
        sendSecurityAlert(user, riskLevel, eventType, null);
    }

    @Transactional
    public void sendSecurityAlert(User user, int riskLevel, String eventType, String eventId) {
        try (var scope = PerformanceLog.scope(eventId, null, null, eventType, null)) {
            PerformanceLog.transaction("FCM_DISPATCH", () -> dispatch(user, riskLevel, eventId));
        }
    }

    private void dispatch(User user, int riskLevel, String eventId) {
        String title = makeTitle(riskLevel);
        String message = makeMessage(riskLevel);
        notificationRepository.save(Notification.builder().user(user).type(NotificationType.SECURITY_EVENT)
                .riskLevel(riskLevel).title(title).message(message).read(false).build());

        List<DeviceToken> deviceTokens = deviceTokenRepository.findAllByUserAndActiveTrue(user);
        for (DeviceToken deviceToken : deviceTokens) {
            pushNotificationPort.send(deviceToken.getToken(), title, message,
                    "SECURITY_EVENT", String.valueOf(riskLevel), eventId);
        }
        PerformanceLog.result(deviceTokens.isEmpty() ? "NO_ACTIVE_TOKENS" : "DISPATCH_RETURNED");
    }

    /** Vision-confirmed observation; does not claim a final Agent assessment. */
    @Transactional
    public boolean sendEarlySecurityAlert(User user, String eventId, String triggerType) {
        try (var scope = PerformanceLog.scope(eventId, null, null, triggerType, null)) {
            return PerformanceLog.measure("EARLY_FCM_DISPATCH", () -> {
                String title = "긴급 조기 경고";
                String message = "Silent Signal이 감지되었습니다. 즉시 주변 상황을 확인해 주세요. 최종 분석은 진행 중입니다.";
                notificationRepository.save(Notification.builder().user(user).type(NotificationType.SECURITY_EVENT)
                        .riskLevel(3).title(title).message(message).read(false).build());
                var tokens = deviceTokenRepository.findAllByUserAndActiveTrue(user);
                for (var token : tokens) pushNotificationPort.send(token.getToken(), title, message,
                        "SECURITY_EVENT", "3", eventId);
                PerformanceLog.result(tokens.isEmpty() ? "NO_ACTIVE_TOKENS" : "DISPATCH_RETURNED");
                return !tokens.isEmpty();
            });
        }
    }

    private String makeTitle(int riskLevel) {
        if (riskLevel >= 3) return "긴급 위험 알림";
        if (riskLevel == 2) return "주의 상황 감지";
        return "보안 이벤트 감지";
    }

    private String makeMessage(int riskLevel) {
        if (riskLevel >= 3) return "위험 상황이 감지되었습니다. 즉시 주변 상황을 확인해 주세요.";
        if (riskLevel == 2) return "주변에 의심스러운 상황이 감지되었습니다.";
        return "보안 이벤트가 감지되었습니다.";
    }
}
