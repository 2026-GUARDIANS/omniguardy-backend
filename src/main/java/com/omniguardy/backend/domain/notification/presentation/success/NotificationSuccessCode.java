package com.omniguardy.backend.domain.notification.presentation.success;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationSuccessCode implements BaseSuccessCode {
    DEVICE_TOKEN_REGISTERED(HttpStatus.CREATED, "DEVICE_TOKEN_REGISTERED", "디바이스 토큰이 등록되었습니다."),
    TEST_NOTIFICATION_SENT(HttpStatus.OK, "TEST_NOTIFICATION_SENT", "테스트 알림이 전송되었습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
