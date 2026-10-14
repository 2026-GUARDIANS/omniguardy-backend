package com.omniguardy.backend.domain.detection.application.model;

import java.time.OffsetDateTime;

public record CameraStartCommand(
        String triggerId,
        TriggerType triggerType,
        String securityEventId,
        OffsetDateTime triggeredAt,
        int duration
) {
}
