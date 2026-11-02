package com.omniguardy.backend.domain.visionsession.domain.model;
import java.time.Instant;
public record VisionFallbackJob(String sessionId, State state, String reason, int attempts, int visionAttempts, int agentAttempts,
        Instant videoDeadline, String leaseToken, Instant leaseUntil, String visionJson, String eventId, Boolean assessmentRequired) {
    public enum State { PENDING, WAITING_VIDEO, RUNNING, PREPARED, CALLING, SUCCEEDED, SKIPPED, FAILED, UNCERTAIN }
}

