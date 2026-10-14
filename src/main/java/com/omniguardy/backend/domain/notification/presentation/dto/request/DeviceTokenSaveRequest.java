package com.omniguardy.backend.domain.notification.presentation.dto.request;

import com.omniguardy.backend.domain.notification.domain.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class DeviceTokenSaveRequest {

    @NotBlank(message = "FCM ?좏겙? ?꾩닔?낅땲??")
    private String token;

    @NotNull(message = "?붾컮?댁뒪 ??낆? ?꾩닔?낅땲??")
    private DeviceType deviceType;
}

