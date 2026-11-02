package com.omniguardy.backend.domain.securityevent.application.port.out;

/** Durable event-wide exclusion. A reserved caller must present its exact reservation token. */
public interface AgentExecutionGuard {
    record Claim(String eventId, String token, boolean alreadyCompleted,
                 com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment completedAssessment) {}
    Claim begin(String eventId, String reservedToken);
    void succeeded(Claim claim);
    void failed(Claim claim, boolean uncertain);
}
