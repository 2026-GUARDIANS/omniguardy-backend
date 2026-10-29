package com.omniguardy.backend.domain.notification.presentation;

import com.omniguardy.backend.domain.notification.presentation.dto.request.DeviceTokenSaveRequest;
import com.omniguardy.backend.domain.notification.application.usecase.DeviceTokenService;
import com.omniguardy.backend.domain.notification.presentation.mapper.NotificationPresentationMapper;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequestMapping("/api/device-tokens")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "사용자 푸시 알림 API")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;
    private final NotificationPresentationMapper mapper;

    @Operation(summary = "FCM 디바이스 토큰 등록", description = "로그인 사용자의 Android/iOS 푸시 토큰을 저장합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = DeviceTokenSaveRequest.class),
                            examples = @ExampleObject(name = "디바이스 토큰", value = DEVICE_TOKEN_REQUEST))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "디바이스 토큰 등록 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.EmptySuccessEnvelope.class),
                            examples = @ExampleObject(name = "DEVICE_TOKEN_REGISTERED", value = DEVICE_TOKEN_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "토큰 누락 또는 지원하지 않는 디바이스 타입",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> saveDeviceToken(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid DeviceTokenSaveRequest request
    ) {
        deviceTokenService.saveDeviceToken(userDetails.getUser(), mapper.toCommand(request));
        return SuccessResponse.of(NotificationSuccessCode.DEVICE_TOKEN_REGISTERED, null);
    }
}

