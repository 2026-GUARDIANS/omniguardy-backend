package com.omniguardy.backend.domain.securityevent.presentation.success;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HomeSuccessCode implements BaseSuccessCode {
    HOME_SUMMARY_RETRIEVED(HttpStatus.OK, "HOME_SUMMARY_RETRIEVED", "홈 화면 요약을 조회했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
