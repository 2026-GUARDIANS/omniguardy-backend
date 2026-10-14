package com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AudioPredictResponseDto {

    private String status;

    @JsonProperty("predicted_class")
    private String predictedClass;

    private Probabilities probabilities;

    @JsonProperty("window_start_seconds")
    private double windowStartSeconds;

    @JsonProperty("cooldown_suppressed")
    private boolean cooldownSuppressed;

    public double getPredictedProbability() {
        return probabilities == null ? 0.0 : probabilities.get(predictedClass);
    }

    @Getter
    @NoArgsConstructor
    public static class Probabilities {

        private double background;
        private double knock;
        private double handle;

        private double get(String predictedClass) {
            if ("background".equals(predictedClass)) return background;
            if ("knock".equals(predictedClass)) return knock;
            if ("handle".equals(predictedClass)) return handle;
            return 0.0;
        }
    }
}
