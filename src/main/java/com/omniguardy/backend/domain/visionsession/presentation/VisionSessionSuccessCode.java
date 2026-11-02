package com.omniguardy.backend.domain.visionsession.presentation;

import com.omniguardy.backend.global.success.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VisionSessionSuccessCode implements BaseSuccessCode {
    VISION_SESSION_REGISTERED(HttpStatus.CREATED, "VISION_SESSION_REGISTERED", "Vision session registered."),
    VISION_SESSION_FOUND(HttpStatus.OK, "VISION_SESSION_FOUND", "Vision session found."),
    VISION_COMPLETION_ACCEPTED(HttpStatus.ACCEPTED, "VISION_COMPLETION_ACCEPTED", "Vision completion notification accepted.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
