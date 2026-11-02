package com.omniguardy.backend.domain.visionsession.domain.model;

import java.time.Instant;

public record StreamingAgentJob(String sessionId, String eventId, State state, int attempts, int agentAttempts,
                                Instant nextAttemptAt, String leaseToken, Instant leaseUntil, String reason,
                                String responseState, boolean fallbackRequired) {
    public enum State { PENDING, PREPARING, CALLING, SUCCEEDED, SKIPPED, BLOCKED, FAILED, UNCERTAIN }
}
