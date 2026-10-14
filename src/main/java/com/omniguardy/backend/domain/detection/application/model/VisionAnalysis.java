package com.omniguardy.backend.domain.detection.application.model;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record VisionAnalysis(

        String triggerId,
        TriggerType triggerType,
        String securityEventId,
        OffsetDateTime triggeredAt,

        OffsetDateTime analyzedAt,

        VisionVideoInfo video,
        VisionBehavior behavior,

        List<VisionEvent> visionEvents,

        VisionObservations observations

) {

    public VisionAnalysis {

        visionEvents =
                visionEvents == null
                        ? Collections.emptyList()
                        : visionEvents;
    }

    // =============================
    // 기존 AUDIO 코드 호환
    // =============================

    public String prediction() {
        return behavior == null
                ? null
                : behavior.prediction();
    }

    public Double confidence() {
        return behavior == null
                ? null
                : behavior.confidence();
    }

    public Map<String, Double> classProbabilities() {
        return behavior == null
                ? Collections.emptyMap()
                : behavior.classProbabilities();
    }

    public Integer personCount() {
        return observations == null
                || observations.trackedPersonCount() == null
                ? 0
                : observations.trackedPersonCount();
    }

    public Double videoDurationSeconds() {
        return video == null
                ? null
                : video.durationSeconds();
    }

    // =============================
    // 신규 Vision Event 처리
    // =============================

    public boolean hasVisionEvents() {
        return !visionEvents.isEmpty();
    }

    public boolean hasSilentSignal() {
        return visionEvents.stream()
                .anyMatch(VisionEvent::isSilentSignal);
    }

    public boolean hasHighRiskKeypadEvent() {
        return visionEvents.stream()
                .anyMatch(VisionEvent::isHighRiskKeypadEvent);
    }

    public boolean requiresAudioRiskAssessment() {
        if (behavior == null || behavior.prediction() == null) {
            return false;
        }
        return "A18".equalsIgnoreCase(behavior.prediction())
                || "A21".equalsIgnoreCase(behavior.prediction());
    }
}
