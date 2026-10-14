package com.omniguardy.backend.domain.ai.presentation.success;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiSuccessCode implements BaseSuccessCode {
    ANALYSIS_COMPLETED(HttpStatus.OK, "AI_ANALYSIS_COMPLETED", "AI 분석이 완료되었습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
