package com.omniguardy.backend.domain.visionsession.application.model;
import java.time.Duration;
public record VisionFallbackPolicy(Duration retryDelay, Duration videoWait, Duration visionLease, Duration agentLease,
        int maxVisionAttempts, int maxPreparationAttempts, int batchSize) {
    public VisionFallbackPolicy {
        for (var value : new Duration[]{retryDelay, videoWait, visionLease, agentLease})
            if (value == null || value.toMillis() < 1) throw new IllegalArgumentException("Fallback durations must be positive");
        if (maxVisionAttempts < 1 || maxPreparationAttempts < 1 || batchSize < 1) throw new IllegalArgumentException("Fallback limits must be positive");
    }
}

