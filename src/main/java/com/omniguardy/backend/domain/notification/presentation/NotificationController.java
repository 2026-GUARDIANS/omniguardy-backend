package com.omniguardy.backend.domain.notification.presentation;

import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.notification.presentation.success.NotificationSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> sendTestNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        notificationService.sendSecurityAlert(
                userDetails.getUser(),
                2,
                "PERSON_DETECTED"
        );
        return SuccessResponse.of(NotificationSuccessCode.TEST_NOTIFICATION_SENT, null);
    }
}

