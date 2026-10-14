package com.omniguardy.backend.domain.detection.application.model;

public record VisionVideoInfo(
        String fileName,
        Double durationSeconds,
        Double fps,
        Integer frameCount
) {
}
