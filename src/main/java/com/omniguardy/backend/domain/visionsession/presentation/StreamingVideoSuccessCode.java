package com.omniguardy.backend.domain.visionsession.presentation;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter @RequiredArgsConstructor
public enum StreamingVideoSuccessCode implements BaseSuccessCode {
    STREAMING_VIDEO_STORED(HttpStatus.CREATED, "STREAMING_VIDEO_STORED", "Streaming MP4 stored."),
    STREAMING_VIDEO_FOUND(HttpStatus.OK, "STREAMING_VIDEO_FOUND", "Streaming video metadata found.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
