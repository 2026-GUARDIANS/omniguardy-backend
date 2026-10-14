package com.omniguardy.backend.global.success;

import com.omniguardy.backend.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;

public final class SuccessResponse {
    private SuccessResponse() {}

    public static <T> ResponseEntity<ApiResponse<T>> of(BaseSuccessCode successCode, T data) {
        return ResponseEntity.status(successCode.getStatus()).body(ApiResponse.success(successCode, data));
    }
}
