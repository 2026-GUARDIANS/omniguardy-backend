package com.omniguardy.backend.domain.auth.presentation;

import com.omniguardy.backend.domain.auth.application.model.AuthSession;
import com.omniguardy.backend.domain.auth.application.usecase.AuthUseCase;
import com.omniguardy.backend.domain.auth.presentation.dto.request.LoginRequestDto;
import com.omniguardy.backend.domain.auth.presentation.dto.request.SignupRequestDto;
import com.omniguardy.backend.domain.auth.presentation.dto.response.LoginResponseDto;
import com.omniguardy.backend.domain.auth.presentation.dto.response.ReissueResponseDto;
import com.omniguardy.backend.domain.auth.presentation.dto.response.SignupResponseDto;
import com.omniguardy.backend.domain.auth.presentation.mapper.AuthPresentationMapper;
import com.omniguardy.backend.domain.auth.presentation.success.AuthSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import com.omniguardy.backend.global.success.SuccessResponse;
import com.omniguardy.backend.global.util.CookieUtil;
import com.omniguardy.backend.global.config.OpenApiSchemas;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "회원가입, 로그인 및 JWT 세션 관리 API")
public class AuthController {
    @org.springframework.beans.factory.annotation.Value("${app.security.cookie-secure:false}")
    private boolean cookieSecure;
    @org.springframework.beans.factory.annotation.Value("${app.security.cookie-same-site:Lax}")
    private String cookieSameSite;
    private final AuthUseCase authUseCase;
    private final AuthPresentationMapper mapper;

    @Operation(summary = "회원가입", description = "이메일 계정을 생성하고 Access Token을 응답합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = SignupRequestDto.class),
                            examples = @ExampleObject(name = "회원가입 요청", value = SIGNUP_REQUEST))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.AuthSessionEnvelope.class),
                            examples = @ExampleObject(name = "SIGNUP_SUCCESS", value = SIGNUP_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이메일 형식 또는 필수 입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "EMAIL_ALREADY_EXISTS", value = EMAIL_ALREADY_EXISTS)))
    })
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponseDto>> signup(@Valid @RequestBody SignupRequestDto request,
                                                                  @Parameter(hidden = true) HttpServletResponse response) {
        AuthSession session = authUseCase.signup(mapper.toCommand(request));
        addRefreshCookie(response, session);
        return SuccessResponse.of(AuthSuccessCode.SIGNUP_SUCCESS, mapper.toSignupResponse(session));
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호를 검증하고 Access Token을 응답합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = LoginRequestDto.class),
                            examples = @ExampleObject(name = "로그인 요청", value = LOGIN_REQUEST))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.AuthSessionEnvelope.class),
                            examples = @ExampleObject(name = "LOGIN_SUCCESS", value = AUTH_SESSION_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이메일 형식 또는 필수 입력값 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_LOGIN_INFO", value = INVALID_LOGIN_INFO)))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto request,
                                                                @Parameter(hidden = true) HttpServletResponse response) {
        AuthSession session = authUseCase.login(mapper.toCommand(request));
        addRefreshCookie(response, session);
        return SuccessResponse.of(AuthSuccessCode.LOGIN_SUCCESS, mapper.toLoginResponse(session));
    }

    @Operation(summary = "Access Token 재발급",
            description = "HttpOnly refreshToken 쿠키를 검증하고 Access Token과 Refresh Token을 재발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공",
                    headers = @Header(name = "Set-Cookie", description = "새 refreshToken HttpOnly 쿠키"),
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.TokenEnvelope.class),
                            examples = @ExampleObject(name = "TOKEN_REISSUE_SUCCESS", value = REISSUE_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh Token 누락, 불일치 또는 만료",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "REFRESH_TOKEN_NOT_FOUND", value = REFRESH_TOKEN_NOT_FOUND),
                            @ExampleObject(name = "REFRESH_TOKEN_MISMATCH", value = REFRESH_TOKEN_MISMATCH),
                            @ExampleObject(name = "REFRESH_TOKEN_EXPIRED", value = REFRESH_TOKEN_EXPIRED)
                    }))
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<ReissueResponseDto>> refresh(
            @Parameter(hidden = true) @CookieValue(value = "refreshToken", required = false) String refreshToken,
            @Parameter(hidden = true) HttpServletResponse response) {
        AuthSession session = authUseCase.reissue(refreshToken);
        addRefreshCookie(response, session);
        return SuccessResponse.of(AuthSuccessCode.TOKEN_REISSUE_SUCCESS, mapper.toReissueResponse(session));
    }

    @Operation(summary = "로그아웃", description = "저장된 Refresh Token을 폐기하고 쿠키를 만료시킵니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.EmptySuccessEnvelope.class),
                            examples = @ExampleObject(name = "LOGOUT_SUCCESS", value = LOGOUT_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Access Token 누락, 만료 또는 위조",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED),
                            @ExampleObject(name = "INVALID_TOKEN", value = INVALID_TOKEN),
                            @ExampleObject(name = "EXPIRED_TOKEN", value = EXPIRED_TOKEN)
                    }))
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(hidden = true) HttpServletResponse response) {
        authUseCase.logout(userDetails.getUserId());
        CookieUtil.deleteRefreshTokenCookie(response, cookieSecure, cookieSameSite);
        return SuccessResponse.of(AuthSuccessCode.LOGOUT_SUCCESS, null);
    }

    private void addRefreshCookie(HttpServletResponse response, AuthSession session) {
        CookieUtil.addRefreshTokenCookie(response, session.refreshToken(), session.refreshTokenMaxAgeSeconds(), cookieSecure, cookieSameSite);
    }
}
