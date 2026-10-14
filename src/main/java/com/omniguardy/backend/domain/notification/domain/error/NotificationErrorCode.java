package com.omniguardy.backend.domain.notification.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {
    PUSH_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "PUSH_DELIVERY_FAILED", "푸시 알림 전송에 실패했습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
