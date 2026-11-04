package com.omniguardy.backend.domain.visionsession.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum StreamingVideoErrorCode implements BaseErrorCode {
    STREAMING_VIDEO_EVENT_CONFLICT(HttpStatus.CONFLICT, "STREAMING_VIDEO_EVENT_CONFLICT", "The security event already references a different video; the stored upload is preserved."),
    INVALID_STREAMING_MP4(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_STREAMING_MP4", "A structurally valid MP4 video with video/mp4 content type and .mp4 filename is required."),
    EMPTY_STREAMING_VIDEO(HttpStatus.BAD_REQUEST, "EMPTY_STREAMING_VIDEO", "The video file is empty."),
    STREAMING_VIDEO_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "STREAMING_VIDEO_TOO_LARGE", "Video size must not exceed 100 MiB."),
    STREAMING_VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "STREAMING_VIDEO_NOT_FOUND", "No streaming video is reserved for this session."),
    STREAMING_VIDEO_CONFLICT(HttpStatus.CONFLICT, "STREAMING_VIDEO_CONFLICT", "This session is already bound to different video content."),
    STREAMING_VIDEO_IN_PROGRESS(HttpStatus.CONFLICT, "STREAMING_VIDEO_IN_PROGRESS", "The upload is in progress. Retry the same video after completion or lease expiry."),
    STREAMING_VIDEO_STORAGE_CHANGED(HttpStatus.CONFLICT, "STREAMING_VIDEO_STORAGE_CHANGED", "The reserved storage destination differs from the current configuration."),
    STREAMING_VIDEO_BINDING_MISMATCH(HttpStatus.CONFLICT, "STREAMING_VIDEO_BINDING_MISMATCH", "The video binding does not match the registered session."),
    STREAMING_VIDEO_STORAGE_FAILED(HttpStatus.BAD_GATEWAY, "STREAMING_VIDEO_STORAGE_FAILED", "Video storage failed. Retry with the same content."),
    STREAMING_VIDEO_DATABASE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "STREAMING_VIDEO_DATABASE_FAILED", "Upload state could not be persisted. Retry with the same content.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
