package com.omniguardy.backend.domain.tts.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TtsErrorCode implements BaseErrorCode {
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "TTS_MESSAGE_TOO_LONG", "TTS 문구는 100자 이하로 입력해 주세요."),
    DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "TTS_DELIVERY_FAILED", "TTS 생성 또는 전송에 실패했습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
