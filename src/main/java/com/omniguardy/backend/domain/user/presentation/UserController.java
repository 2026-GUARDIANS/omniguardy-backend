package com.omniguardy.backend.domain.user.presentation;

import com.omniguardy.backend.domain.user.application.port.in.QueryCurrentUserInputPort;
import com.omniguardy.backend.domain.user.presentation.dto.response.CurrentUserResponse;
import com.omniguardy.backend.domain.user.presentation.success.UserSuccessCode;
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
import static com.omniguardy.backend.global.config.OpenApiExamples.CURRENT_USER_SUCCESS;
import static com.omniguardy.backend.global.config.OpenApiExamples.USER_NOT_FOUND;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 정보 조회 API")
public class UserController {
    private final QueryCurrentUserInputPort queryCurrentUser;

    @Operation(summary = "내 정보 조회", description = "인증된 사용자의 이름, 이메일, 연락처를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.CurrentUserEnvelope.class),
                            examples = @ExampleObject(name = "CURRENT_USER_RETRIEVED", value = CURRENT_USER_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "USER_NOT_FOUND", value = USER_NOT_FOUND)))
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal) {
        return SuccessResponse.of(UserSuccessCode.CURRENT_USER_RETRIEVED,
                CurrentUserResponse.from(queryCurrentUser.query(principal.getUserId())));
    }
}
