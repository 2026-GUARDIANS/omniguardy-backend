package com.omniguardy.backend.domain.notification.application.port.out;

public interface PushNotificationPort {
    void send(String token, String title, String body, String type, String riskLevel);
}
