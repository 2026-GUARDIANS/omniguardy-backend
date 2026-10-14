package com.omniguardy.backend.global.error.response;

import com.omniguardy.backend.global.error.BaseErrorCode;

public record ErrorResponse(boolean success, int status, String code, String message, Object data) {
    public static ErrorResponse from(BaseErrorCode errorCode) {
        return new ErrorResponse(false, errorCode.getStatus().value(), errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(BaseErrorCode errorCode, String message) {
        return new ErrorResponse(false, errorCode.getStatus().value(), errorCode.getCode(), message, null);
    }
}
