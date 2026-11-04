package com.omniguardy.backend.domain.notification.infrastructure.fcm;

import com.omniguardy.backend.domain.notification.domain.error.NotificationErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.domain.notification.application.port.out.PushNotificationPort;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import com.omniguardy.backend.global.performance.PerformanceLog;

@Service
public class FirebasePushAdapter implements PushNotificationPort {

    @Override
    public void send(
            String token,
            String title,
            String body,
            String type,
            String riskLevel,
            String eventId
    ) {
        try (var scope = PerformanceLog.scope(eventId, null, null, null, null)) {
            PerformanceLog.run("FCM_SEND_REQUEST", () -> {
                try {
                    sendRequest(token, title, body, type, riskLevel, eventId);
                    PerformanceLog.result("PROVIDER_API_RETURNED");
                    PerformanceLog.elapsed("EVENT_TO_FCM_REQUEST", true, null, "FCM_PROVIDER_API_RETURN");
                } catch (RuntimeException | Error failure) {
                    PerformanceLog.elapsed("EVENT_TO_FCM_REQUEST", false, failure, "FCM_PROVIDER_API_RETURN");
                    throw failure;
                }
            });
        }
    }

    private void sendRequest(String token, String title, String body, String type, String riskLevel, String eventId) {
        try {
            Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(
                            Notification.builder()
                                    .setTitle(title)
                                    .setBody(body)
                                    .build()
                    )
                    .putData("type", type)
                    .putData("riskLevel", riskLevel);
            // The existing test notification has no associated security event.
            if (eventId != null) builder.putData("eventId", eventId);
            Message message = builder.build();

            FirebaseMessaging.getInstance().send(message);

        } catch (Exception e) {
            throw new BusinessException(NotificationErrorCode.PUSH_DELIVERY_FAILED, e);
        }
    }
}

