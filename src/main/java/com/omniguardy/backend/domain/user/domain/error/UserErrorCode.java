package com.omniguardy.backend.domain.user.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    DEMO_USER_ID_NOT_CONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "DEMO_USER_ID_NOT_CONFIGURED",
            "시연 사용자 설정 omniguardy.demo.user-id가 필요합니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
