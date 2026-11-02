package com.omniguardy.backend.domain.visionsession.domain.model;

import java.time.Instant;

public record VisionPollingJob(VisionBinding binding, VisionProcessingState state, Instant requestedAt,
                               Instant deadlineAt, Instant nextPollAt, Instant leaseUntil, String leaseToken,
                               int attempts, String reason, String lastError, String lastStatusJson) {}
