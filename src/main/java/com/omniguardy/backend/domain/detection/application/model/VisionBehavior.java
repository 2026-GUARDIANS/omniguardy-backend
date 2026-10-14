package com.omniguardy.backend.domain.detection.application.model;

import java.util.Map;

public record VisionBehavior(
        String prediction,
        String label,
        Double confidence,
        Map<String, Double> classProbabilities
) {
}
