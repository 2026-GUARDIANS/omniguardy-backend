package com.omniguardy.backend.domain.tts.presentation.success;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TtsSuccessCode implements BaseSuccessCode {
    MESSAGE_PUBLISHED(HttpStatus.OK, "TTS_MESSAGE_PUBLISHED", "TTS 메시지가 전송되었습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
