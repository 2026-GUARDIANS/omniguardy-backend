package com.omniguardy.backend.domain.notification.presentation.dto.request;

import com.omniguardy.backend.domain.notification.domain.model.DeviceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class DeviceTokenSaveRequest {

    @Schema(description = "Firebase Cloud Messaging 디바이스 토큰",
            example = "fcm-device-token-2xYz9KpQ_example")
    @NotBlank(message = "FCM ?좏겙? ?꾩닔?낅땲??")
    private String token;

    @Schema(description = "모바일 운영체제", example = "ANDROID", allowableValues = {"ANDROID", "IOS"})
    @NotNull(message = "?붾컮?댁뒪 ??낆? ?꾩닔?낅땲??")
    private DeviceType deviceType;
}

