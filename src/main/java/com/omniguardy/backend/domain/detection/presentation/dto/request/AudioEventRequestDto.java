package com.omniguardy.backend.domain.detection.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.omniguardy.backend.domain.detection.application.model.AudioAnalysis;
import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AudioEventRequestDto(
        @Schema(description = "FastAPI 분석 요청 식별자", example = "audio-20261029-001")
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String detectionId,
        @Schema(description = "분석 처리 상태", example = "success")
        String status,
        @Schema(description = "가장 높은 확률의 분류", example = "knock",
                allowableValues = {"knock", "handle", "background"})
        @JsonProperty("predicted_class") @NotNull @Pattern(regexp = "knock|handle|background") String predictedClass,
        @NotNull @Valid Probabilities probabilities,
        @Schema(description = "분석 윈도우 시작 시각(초)", example = "1.5")
        @JsonProperty("window_start_seconds") Double windowStartSeconds,
        @Schema(description = "쿨다운으로 이벤트 발생이 억제됐는지 여부", example = "false")
        @JsonProperty("cooldown_suppressed") Boolean cooldownSuppressed
) {
    public AudioAnalysis toAnalysis() {
        double probability = switch (predictedClass) {
            case "knock" -> probabilities.knock();
            case "handle" -> probabilities.handle();
            default -> probabilities.background();
        };
        return new AudioAnalysis(AudioEventType.fromPredictedClass(predictedClass), probability);
    }

    public record Probabilities(
            @Schema(description = "배경음 확률", example = "0.02")
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double background,
            @Schema(description = "노크 확률", example = "0.94")
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double knock,
            @Schema(description = "손잡이 조작음 확률", example = "0.04")
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double handle
    ) {}
}
