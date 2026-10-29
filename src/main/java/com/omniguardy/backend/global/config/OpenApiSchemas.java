package com.omniguardy.backend.global.config;

import com.omniguardy.backend.domain.ai.presentation.dto.response.AiAnalyzeResponse;
import com.omniguardy.backend.domain.detection.application.model.AudioDetectionResult;
import com.omniguardy.backend.domain.detection.presentation.dto.response.AudioUploadResponseDto;
import com.omniguardy.backend.domain.detection.presentation.dto.response.VideoUploadResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;

/** Documentation-only concrete response types for generic ApiResponse payloads. */
public final class OpenApiSchemas {

    private OpenApiSchemas() {
    }

    @Schema(name = "AuthSessionApiResponse")
    public record AuthSessionEnvelope(
            @Schema(example = "true") boolean success,
            @Schema(example = "LOGIN_SUCCESS") String code,
            @Schema(example = "로그인에 성공했습니다.") String message,
            AuthSessionData data
    ) {
    }

    public record AuthSessionData(
            @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJndWFyZEBleGFtcGxlLmNvbSJ9.signature") String accessToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(example = "17") Long userId,
            @Schema(example = "guard@example.com") String email,
            @Schema(example = "김가디") String name
    ) {
    }

    @Schema(name = "TokenReissueApiResponse")
    public record TokenEnvelope(
            @Schema(example = "true") boolean success,
            @Schema(example = "TOKEN_REISSUE_SUCCESS") String code,
            @Schema(example = "토큰이 재발급되었습니다.") String message,
            TokenData data
    ) {
    }

    public record TokenData(
            @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJndWFyZEBleGFtcGxlLmNvbSJ9.signature") String accessToken,
            @Schema(example = "Bearer") String tokenType
    ) {
    }

    @Schema(name = "AiAnalyzeApiResponse")
    public record AiAnalyzeEnvelope(boolean success, String code, String message, AiAnalyzeResponse data) {
    }

    @Schema(name = "AudioUploadApiResponse")
    public record AudioUploadEnvelope(boolean success, String code, String message, AudioUploadResponseDto data) {
    }

    @Schema(name = "VideoUploadApiResponse")
    public record VideoUploadEnvelope(boolean success, String code, String message, VideoUploadResponseDto data) {
    }

    @Schema(name = "AudioResultApiResponse")
    public record AudioResultEnvelope(boolean success, String code, String message, AudioResultData data) {
    }

    public record AudioResultData(
            @Schema(example = "audio-20261029-001") String detectionId,
            @Schema(example = "evt-audio-20261029-001") String eventId,
            @Schema(example = "TRIGGERED") AudioDetectionResult.Outcome outcome
    ) {
    }

    @Schema(name = "EmptySuccessApiResponse")
    public record EmptySuccessEnvelope(
            @Schema(example = "true") boolean success,
            @Schema(example = "LOGOUT_SUCCESS") String code,
            @Schema(example = "로그아웃이 완료되었습니다.") String message,
            @Schema(nullable = true, example = "null") Object data
    ) {
    }
}
