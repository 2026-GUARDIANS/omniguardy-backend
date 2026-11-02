package com.omniguardy.backend.domain.visionsession.domain.model;

import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** An immutable, provisional STREAMING observation, independent of final analysis. */
public record VisionEarlyEvent(String sessionId, String triggerId, String securityEventId,
                               VisionTriggerType triggerType, String eventInstanceId, long sequence,
                               long detectedFrame, long confirmedFrame, String capturedAt, String emittedAt) {
    public VisionEarlyEvent {
        if (!VisionSession.validId(sessionId, 128) || !VisionSession.validId(triggerId, 64)
                || triggerType == null || eventInstanceId == null || eventInstanceId.isBlank()
                || eventInstanceId.length() > 256 || emittedAt == null || emittedAt.length() > 64
                || (securityEventId != null && !VisionSession.validId(securityEventId, 36))) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        try {
            OffsetDateTime.parse(emittedAt);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
    }
}
