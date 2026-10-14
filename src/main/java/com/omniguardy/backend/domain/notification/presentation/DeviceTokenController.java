package com.omniguardy.backend.domain.notification.presentation;

import com.omniguardy.backend.domain.notification.presentation.dto.request.DeviceTokenSaveRequest;
import com.omniguardy.backend.domain.notification.application.usecase.DeviceTokenService;
import com.omniguardy.backend.domain.notification.presentation.mapper.NotificationPresentationMapper;
import com.omniguardy.backend.domain.notification.presentation.success.NotificationSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.success.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device-tokens")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final NotificationPresentationMapper mapper;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveDeviceToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid DeviceTokenSaveRequest request
    ) {
        deviceTokenService.saveDeviceToken(userDetails.getUser(), mapper.toCommand(request));
        return SuccessResponse.of(NotificationSuccessCode.DEVICE_TOKEN_REGISTERED, null);
    }
}

