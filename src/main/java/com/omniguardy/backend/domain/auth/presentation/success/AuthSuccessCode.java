package com.omniguardy.backend.domain.auth.presentation.success;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {
    SIGNUP_SUCCESS(HttpStatus.CREATED, "SIGNUP_SUCCESS", "회원가입이 완료되었습니다."),
    LOGIN_SUCCESS(HttpStatus.OK, "LOGIN_SUCCESS", "로그인에 성공했습니다."),
    TOKEN_REISSUE_SUCCESS(HttpStatus.OK, "TOKEN_REISSUE_SUCCESS", "토큰이 재발급되었습니다."),
    LOGOUT_SUCCESS(HttpStatus.OK, "LOGOUT_SUCCESS", "로그아웃이 완료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
