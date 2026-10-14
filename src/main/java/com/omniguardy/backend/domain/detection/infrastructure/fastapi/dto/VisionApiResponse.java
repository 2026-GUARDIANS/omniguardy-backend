package com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record VisionApiResponse(
        String status,
        Result result
) {

    public record Result(
            Trigger trigger,
            OffsetDateTime analyzedAt,
            Video video,
            Behavior behavior,
            List<VisionEventDto> visionEvents,
            Observations observations
    ) {
    }

    public record Trigger(
            String triggerId,
            String triggerType,
            String securityEventId,
            OffsetDateTime triggeredAt
    ) {
    }

    public record Video(
            String fileName,
            Double durationSeconds,
            Double fps,
            Integer frameCount
    ) {
    }

    public record Behavior(
            String prediction,
            String label,
            Double confidence,
            Map<String, Double> classProbabilities
    ) {
    }

    public record VisionEventDto(
            String eventType,
            Double confidence,
            Integer detectedFrame,
            Map<String, Object> details
    ) {
    }

    public record Observations(
            Integer trackedPersonCount,
            Boolean hasTracking,
            Boolean hasPose
    ) {
    }
}
