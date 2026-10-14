package com.omniguardy.backend.domain.detection.application.model;

public record VisionObservations(
        Integer trackedPersonCount,
        Boolean hasTracking,
        Boolean hasPose
) {
}
