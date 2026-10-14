package com.omniguardy.backend.domain.notification.application.model;

import com.omniguardy.backend.domain.notification.domain.model.DeviceType;

public record RegisterDeviceTokenCommand(String token, DeviceType deviceType) {
}
