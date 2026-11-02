package com.omniguardy.backend.domain.visionsession.domain.error;

import com.omniguardy.backend.global.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VisionEarlyEventErrorCode implements BaseErrorCode {
    VISION_CALLBACK_BINDING_MISMATCH(HttpStatus.CONFLICT, "VISION_CALLBACK_BINDING_MISMATCH",
            "Callback identifiers do not match the registered session and trigger."),
    VISION_EARLY_EVENT_CONFLICT(HttpStatus.CONFLICT, "VISION_EARLY_EVENT_CONFLICT",
            "The early event identifier is already stored with a different payload.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
