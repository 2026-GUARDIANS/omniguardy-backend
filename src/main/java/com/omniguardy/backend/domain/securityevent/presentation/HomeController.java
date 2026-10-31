package com.omniguardy.backend.domain.securityevent.presentation;

import com.omniguardy.backend.domain.securityevent.application.model.HomeSummaryResponse;
import com.omniguardy.backend.domain.securityevent.application.port.in.QueryHomeSummaryInputPort;
import com.omniguardy.backend.domain.securityevent.presentation.success.HomeSuccessCode;
import com.omniguardy.backend.global.config.OpenApiSchemas;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.success.SuccessResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.omniguardy.backend.global.config.OpenApiExamples.AUTHENTICATION_REQUIRED;
import static com.omniguardy.backend.global.config.OpenApiExamples.HOME_SUMMARY_SUCCESS;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
@Tag(name = "Home", description = "홈 화면 조회 API")
public class HomeController {
    private final QueryHomeSummaryInputPort queryHomeSummary;

    @Operation(summary = "홈 화면 요약 조회", description = "현재 사용자의 최신 보안 상태와 오늘 이벤트 수를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "홈 화면 요약 조회 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.HomeSummaryEnvelope.class),
                            examples = @ExampleObject(name = "HOME_SUMMARY_RETRIEVED", value = HOME_SUMMARY_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED)))
    })
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> summary(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal) {
        return SuccessResponse.of(HomeSuccessCode.HOME_SUMMARY_RETRIEVED,
                queryHomeSummary.query(principal.getUserId()));
    }
}
