package com.omniguardy.backend.domain.detection.application.model;

public record VideoTriggerContext(
        String eventId,
        String triggerId,
        TriggerType triggerType,
        String securityEventId,
        String triggeredAt
) {
}
