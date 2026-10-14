package com.omniguardy.backend.domain.detection.presentation.success;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DetectionSuccessCode implements BaseSuccessCode {
    AUDIO_RECEIVED(HttpStatus.OK, "AUDIO_RECEIVED", "오디오 파일 수신에 성공했습니다."),
    VIDEO_ANALYZED(HttpStatus.OK, "VIDEO_ANALYZED", "영상 분석이 완료되었습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
