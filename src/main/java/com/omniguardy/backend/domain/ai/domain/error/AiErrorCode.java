package com.omniguardy.backend.domain.ai.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements BaseErrorCode {
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."),
    MEDIA_FILE_REQUIRED(HttpStatus.BAD_REQUEST, "MEDIA_FILE_REQUIRED", "분석할 미디어 파일은 필수입니다."),
    VISION_API_FAILED(HttpStatus.BAD_GATEWAY, "VISION_API_FAILED", "영상 AI 서버 처리에 실패했습니다."),
    AUDIO_API_FAILED(HttpStatus.BAD_GATEWAY, "AUDIO_API_FAILED", "오디오 AI 서버 처리에 실패했습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
