package com.omniguardy.backend.domain.securityevent.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Durable, per-trigger channel reservations shared by early and final responses. */
public interface SecurityResponseLedger {
    enum Channel { FCM, SOUND }
    record Binding(String key, String eventId, String triggerId, String sessionId, String triggerType,
                   Instant receivedAt) {}
    record Claim(Binding binding, Channel channel, int level, String token) {}
    void enqueueEarly(String sessionId);
    List<String> undispatchedSessions(int limit);
    void linkCreatedEvents();
    List<String> pending(Channel channel, int limit);
    Optional<Claim> claimEarly(String key, Channel channel);
    Optional<Claim> claimFinal(Binding binding, Channel channel, int level);
    void finished(Claim claim, String outcome);
}
