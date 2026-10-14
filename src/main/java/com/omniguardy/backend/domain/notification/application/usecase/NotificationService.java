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

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushNotificationPort pushNotificationPort;

    @Transactional
    public void sendSecurityAlert(User user, int riskLevel, String eventType) {
        String title = makeTitle(riskLevel);
        String message = makeMessage(riskLevel);
        notificationRepository.save(Notification.builder().user(user).type(NotificationType.SECURITY_EVENT)
                .riskLevel(riskLevel).title(title).message(message).read(false).build());

        List<DeviceToken> deviceTokens = deviceTokenRepository.findAllByUserAndActiveTrue(user);
        for (DeviceToken deviceToken : deviceTokens) {
            pushNotificationPort.send(deviceToken.getToken(), title, message,
                    "SECURITY_EVENT", String.valueOf(riskLevel));
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
