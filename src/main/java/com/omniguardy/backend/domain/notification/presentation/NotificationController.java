package com.omniguardy.backend.domain.notification.presentation;

import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.notification.presentation.success.NotificationSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.success.SuccessResponse;
import com.omniguardy.backend.global.config.OpenApiSchemas;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "사용자 푸시 알림 API")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "테스트 푸시 알림", description = "현재 로그인 사용자의 등록된 디바이스로 테스트 보안 알림을 전송합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "알림 전송 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.EmptySuccessEnvelope.class),
                            examples = @ExampleObject(name = "TEST_NOTIFICATION_SENT", value = NOTIFICATION_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "FCM 푸시 전송 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "PUSH_DELIVERY_FAILED", value = PUSH_DELIVERY_FAILED)))
    })
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> sendTestNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        notificationService.sendSecurityAlert(
                userDetails.getUser(),
                2,
                "PERSON_DETECTED"
        );
        return SuccessResponse.of(NotificationSuccessCode.TEST_NOTIFICATION_SENT, null);
    }
}

