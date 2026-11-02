package com.omniguardy.backend.domain.visionsession.application.model;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import java.time.Instant;

public record VisionProcessingView(String sessionId, VisionProcessingState processingState,
        Instant requestedAt, Instant deadlineAt, int attempts, boolean fallbackRequired, String reason) {
    public static VisionProcessingView from(VisionPollingJob job) {
        return new VisionProcessingView(job.binding().sessionId(), job.state(), job.requestedAt(), job.deadlineAt(),
                job.attempts(), job.state() == VisionProcessingState.FALLBACK_REQUIRED, job.reason());
    }
}
