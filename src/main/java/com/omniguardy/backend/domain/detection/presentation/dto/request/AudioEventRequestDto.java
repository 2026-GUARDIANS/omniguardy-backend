package com.omniguardy.backend.domain.detection.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.omniguardy.backend.domain.detection.application.model.AudioAnalysis;
import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AudioEventRequestDto(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String detectionId,
        String status,
        @JsonProperty("predicted_class") @NotNull @Pattern(regexp = "knock|handle|background") String predictedClass,
        @NotNull @Valid Probabilities probabilities,
        @JsonProperty("window_start_seconds") Double windowStartSeconds,
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
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double background,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double knock,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double handle
    ) {}
}
