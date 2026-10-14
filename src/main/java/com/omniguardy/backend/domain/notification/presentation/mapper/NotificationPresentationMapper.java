package com.omniguardy.backend.domain.notification.presentation.mapper;

import com.omniguardy.backend.domain.notification.application.model.RegisterDeviceTokenCommand;
import com.omniguardy.backend.domain.notification.presentation.dto.request.DeviceTokenSaveRequest;
import org.springframework.stereotype.Component;

@Component
public class NotificationPresentationMapper {
    public RegisterDeviceTokenCommand toCommand(DeviceTokenSaveRequest request) {
        return new RegisterDeviceTokenCommand(request.getToken(), request.getDeviceType());
    }
}
