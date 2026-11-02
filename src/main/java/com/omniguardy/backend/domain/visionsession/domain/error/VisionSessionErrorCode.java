package com.omniguardy.backend.domain.visionsession.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VisionSessionErrorCode implements BaseErrorCode {
    VISION_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "VISION_SESSION_NOT_FOUND", "Vision session not found."),
    VISION_TRIGGER_NOT_FOUND(HttpStatus.NOT_FOUND, "VISION_TRIGGER_NOT_FOUND", "Vision trigger not found."),
    INVALID_VISION_TRIGGER(HttpStatus.BAD_REQUEST, "INVALID_VISION_TRIGGER", "Trigger is not eligible for a streaming Vision session."),
    VISION_SESSION_CONFLICT(HttpStatus.CONFLICT, "VISION_SESSION_CONFLICT", "Session or trigger is already bound to a different registration."),
    INVALID_VISION_SESSION_STATUS(HttpStatus.CONFLICT, "INVALID_VISION_SESSION_STATUS", "A terminal Vision session cannot change status.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
