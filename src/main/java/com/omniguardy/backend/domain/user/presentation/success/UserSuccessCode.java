package com.omniguardy.backend.domain.user.presentation.success;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserSuccessCode implements BaseSuccessCode {
    CURRENT_USER_RETRIEVED(HttpStatus.OK, "CURRENT_USER_RETRIEVED", "사용자 정보를 조회했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
