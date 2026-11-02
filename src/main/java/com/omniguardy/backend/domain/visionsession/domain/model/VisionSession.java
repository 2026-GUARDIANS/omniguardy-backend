package com.omniguardy.backend.domain.visionsession.domain.model;

import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import java.time.Instant;
import java.util.Objects;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;

/** A STREAMING session. Registering one never creates or updates a SecurityEvent. */
public record VisionSession(String sessionId, VisionTriggerType triggerType, String eventId,
                            String triggerId, VisionSessionStatus status, Instant registeredAt) {
    public VisionSession {
        if (!validId(sessionId, 128) || triggerType == null || status == null || registeredAt == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        if (triggerType == VisionTriggerType.AUDIO
                ? !validId(eventId, 36) || triggerId != null
                : !validId(triggerId, 64) || eventId != null) {
            throw new BusinessException(INVALID_VISION_TRIGGER);
        }
    }

    public static boolean validId(String id, int maxLength) {
        return id != null && id.length() <= maxLength && id.matches("[A-Za-z0-9][A-Za-z0-9._:-]*");
    }

    public static VisionSession register(String sessionId, VisionTriggerType type, String eventId, String triggerId) {
        // Database DATETIME(6) precision also applies to the first response and all retries.
        return new VisionSession(sessionId, type, eventId, triggerId, VisionSessionStatus.REGISTERED,
                Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    public boolean hasSameBinding(VisionSession other) {
        return sessionId.equals(other.sessionId) && triggerType == other.triggerType
                && Objects.equals(eventId, other.eventId) && Objects.equals(triggerId, other.triggerId);
    }

    /** For future result handlers; no status mutation API is exposed in phase 1. */
    public VisionSession complete() { return finish(VisionSessionStatus.COMPLETED); }
    public VisionSession fail() { return finish(VisionSessionStatus.FAILED); }

    private VisionSession finish(VisionSessionStatus next) {
        if (status == next) return this;
        if (status != VisionSessionStatus.REGISTERED) throw new BusinessException(INVALID_VISION_SESSION_STATUS);
        return new VisionSession(sessionId, triggerType, eventId, triggerId, next, registeredAt);
    }
}
