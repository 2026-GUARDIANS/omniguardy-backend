package com.omniguardy.backend.domain.detection.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DetectionErrorCode implements BaseErrorCode {
    EMPTY_VIDEO_FILE(HttpStatus.BAD_REQUEST, "EMPTY_VIDEO_FILE", "영상 파일이 비어 있습니다."),
    EMPTY_AUDIO_FILE(HttpStatus.BAD_REQUEST, "EMPTY_AUDIO_FILE", "오디오 파일이 비어 있습니다."),
    EVENT_ID_REQUIRED(HttpStatus.BAD_REQUEST, "EVENT_ID_REQUIRED", "eventId는 필수입니다."),
    TRIGGER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "TRIGGER_ID_REQUIRED", "triggerId는 필수입니다."),
    TRIGGERED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "TRIGGERED_AT_REQUIRED", "triggeredAt은 필수입니다."),
    VIDEO_FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "VIDEO_FILE_TOO_LARGE", "영상 파일은 100MB 이하여야 합니다."),
    MEDIA_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_STORAGE_FAILED", "미디어 파일 저장에 실패했습니다."),
    VISION_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "VISION_ANALYSIS_FAILED", "영상 분석 서버 처리에 실패했습니다."),
    VISION_RESPONSE_CONTRACT_INVALID(HttpStatus.BAD_GATEWAY, "VISION_RESPONSE_CONTRACT_INVALID", "Vision 응답이 지원하는 계약과 일치하지 않습니다."),
    AUDIO_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "AUDIO_ANALYSIS_FAILED", "오디오 분석 서버 처리에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
