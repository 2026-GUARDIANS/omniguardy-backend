package com.omniguardy.backend.domain.securityevent.presentation;

import com.omniguardy.backend.domain.securityevent.application.port.in.QueryVideoUrlInputPort;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@Profile("prod")
@RequiredArgsConstructor
@Tag(name = "Security Event", description = "사용자별 보안 이벤트 조회 API")
public class VideoUrlController {
    private final QueryVideoUrlInputPort query;
    @Operation(summary = "보안 이벤트 영상 URL 조회",
            description = "현재 사용자가 소유한 이벤트 영상의 5분 유효 S3 Presigned URL을 발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영상 URL 발급 성공",
                    content = @Content(examples = @ExampleObject(name = "Presigned URL", value = VIDEO_URL_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트 또는 저장 영상 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "SECURITY_EVENT_NOT_FOUND", value = SECURITY_EVENT_NOT_FOUND)))
    })
    @GetMapping("/api/security-events/{eventId}/video-url")
    public ResponseEntity<Map<String, String>> query(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user,
            @Parameter(description = "보안 이벤트 공개 식별자", example = "evt-video-20261029-001", required = true)
            @PathVariable String eventId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("url", query.query(eventId, user.getUserId())));
    }
}
