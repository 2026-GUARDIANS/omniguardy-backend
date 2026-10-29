package com.omniguardy.backend.domain.securityevent.presentation;

import com.omniguardy.backend.domain.securityevent.application.model.*;
import com.omniguardy.backend.domain.securityevent.application.port.in.QuerySecurityEventsInputPort;
import lombok.RequiredArgsConstructor;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequestMapping("/api/security-events")
@RequiredArgsConstructor
@Tag(name = "Security Event", description = "사용자별 보안 이벤트 조회 API")
public class SecurityEventController {
    private final QuerySecurityEventsInputPort query;

    @Operation(summary = "보안 이벤트 목록 조회", description = "현재 사용자의 보안 이벤트를 최신순으로 페이지 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = SecurityEventPageResponse.class),
                            examples = @ExampleObject(name = "첫 페이지", value = SECURITY_EVENT_LIST_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "page 또는 size 범위 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED)))
    })
    @GetMapping
    public SecurityEventPageResponse list(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(1~100)", example = "20") @RequestParam(defaultValue = "20") int size) {
        return query.list(principal.getUserId(), page, size);
    }

    @Operation(summary = "보안 이벤트 상세 조회", description = "현재 사용자가 소유한 단일 보안 이벤트의 분석 결과를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상세 조회 성공",
                    content = @Content(schema = @Schema(implementation = SecurityEventResponse.class),
                            examples = @ExampleObject(name = "분석 완료 이벤트", value = SECURITY_EVENT_DETAIL_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이벤트가 없거나 현재 사용자 소유가 아님",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "SECURITY_EVENT_NOT_FOUND", value = SECURITY_EVENT_NOT_FOUND)))
    })
    @GetMapping("/{eventId}")
    public SecurityEventResponse detail(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "보안 이벤트 공개 식별자", example = "evt-video-20261029-001", required = true)
            @PathVariable String eventId) {
        return query.detail(principal.getUserId(), eventId);
    }
}
