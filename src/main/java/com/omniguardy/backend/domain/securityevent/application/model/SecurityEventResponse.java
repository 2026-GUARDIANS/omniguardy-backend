package com.omniguardy.backend.domain.securityevent.application.model;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/** Explicit allowlist: excludes user details, credentials and media storage paths. */
public record SecurityEventResponse(
        Long id, String eventId, Long userId, String triggerId, EventTriggerType triggerType,
        String status, AudioEventType audioEventType, Double audioProbability,
        String visionPrediction, Double visionConfidence, String classProbabilities,
        String visionEvents, Integer personCount, Double videoDurationSeconds,
        String riskLevel, Integer riskScore, String agentReason,
        OffsetDateTime triggeredAt, OffsetDateTime visionAnalyzedAt,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static SecurityEventResponse from(SecurityEvent e) {
        return new SecurityEventResponse(e.getId(), e.getEventId(),
                e.getUser() == null ? null : e.getUser().getId(), e.getTriggerId(), e.getTriggerType(),
                e.getStatus(), e.getAudioEventType(), e.getAudioProbability(),
                e.getVisionPrediction(), e.getVisionConfidence(), e.getClassProbabilities(),
                e.getVisionEvents(), e.getPersonCount(), e.getVideoDurationSeconds(),
                e.getRiskLevel(), e.getRiskScore(), e.getAgentReason(),
                e.getTriggeredAt(), e.getVisionAnalyzedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
