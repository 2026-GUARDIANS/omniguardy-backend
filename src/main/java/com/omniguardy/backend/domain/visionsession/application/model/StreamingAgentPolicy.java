package com.omniguardy.backend.domain.visionsession.application.model;

import java.time.Duration;

public record StreamingAgentPolicy(Duration preparationLease, Duration callingLease, Duration retryDelay,
                                   int maxAttempts, int batchSize) {
    public StreamingAgentPolicy {
        if (preparationLease == null || preparationLease.toMillis() < 1 || callingLease == null || callingLease.toMillis() < 1
                || retryDelay == null || retryDelay.toMillis() < 1 || maxAttempts < 1 || maxAttempts > 10
                || batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Invalid streaming Agent policy");
    }
}
