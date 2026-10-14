package com.omniguardy.backend.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.omniguardy.backend.global.success.BaseSuccessCode;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(BaseSuccessCode successCode, T data) {
        return new ApiResponse<>(true, successCode.getCode(), successCode.getMessage(), data);
    }
}
