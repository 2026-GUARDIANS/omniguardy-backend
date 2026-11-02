package com.omniguardy.backend.domain.visionsession.application.model;

import java.time.Duration;

public record VisionPollingPolicy(Duration interval, Duration maxWait, Duration leaseDuration, int batchSize) {
    public VisionPollingPolicy {
        if (interval == null || interval.toMillis() < 1 || maxWait == null || maxWait.toMillis() < 1
                || leaseDuration == null || leaseDuration.toMillis() < 1 || batchSize < 1 || batchSize > 100)
            throw new IllegalArgumentException("Invalid streaming Vision polling policy");
    }
}
