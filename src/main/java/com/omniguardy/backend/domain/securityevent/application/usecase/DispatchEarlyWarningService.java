package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.port.in.DispatchEarlyWarningInputPort;
import com.omniguardy.backend.domain.securityevent.application.port.out.*;
import com.omniguardy.backend.global.performance.PerformanceLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class DispatchEarlyWarningService implements DispatchEarlyWarningInputPort {
    private final SecurityResponseLedger ledger;
    private final EarlyWarningDeliveryPort delivery;

    @Override public void enqueue(String sessionId) { ledger.enqueueEarly(sessionId); }

    @Override public void recover() {
        ledger.linkCreatedEvents();
        for (String session : ledger.undispatchedSessions(100)) {
            try { ledger.enqueueEarly(session); }
            catch (RuntimeException failure) { log.warn("Early warning enqueue deferred failureType={}", failure.getClass().getSimpleName()); }
        }
    }

    @Override public void dispatch(SecurityResponseLedger.Channel channel) {
        for (String key : ledger.pending(channel, 20)) {
            try { ledger.claimEarly(key, channel).ifPresent(this::deliver); }
            catch (RuntimeException failure) { log.warn("Early warning dispatch deferred channel={} failureType={}", channel, failure.getClass().getSimpleName()); }
        }
    }

    private void deliver(SecurityResponseLedger.Claim claim) {
        var b = claim.binding();
        String stage = claim.channel() == SecurityResponseLedger.Channel.FCM ? "EARLY_EVENT_FCM_REQUEST" : "EARLY_EVENT_MQTT_SIREN";
        String point = claim.channel() == SecurityResponseLedger.Channel.FCM ? "FCM_PROVIDER_API_RETURN" : "MQTT_CLIENT_HANDOFF";
        try (var scope = PerformanceLog.scope(b.eventId(), b.triggerId(), b.sessionId(), b.triggerType(), 1)) {
            PerformanceLog.observedAt(b.receivedAt(), "EARLY_EVENT_RECEIVED_AT");
            try {
                String result = PerformanceLog.measure(stage, () -> {
                    String outcome;
                    if (claim.channel() == SecurityResponseLedger.Channel.FCM) outcome = delivery.sendPush(b);
                    else { delivery.sendSiren(); outcome = "REQUEST_ACCEPTED"; }
                    PerformanceLog.result(outcome);
                    return outcome;
                });
                if (result.equals("REQUEST_ACCEPTED"))
                    PerformanceLog.elapsed("EARLY_EVENT_TO_" + claim.channel().name(), true, null, point);
                ledger.finished(claim, result.equals("REQUEST_ACCEPTED") ? "SUCCEEDED" : "SKIPPED");
            } catch (RuntimeException failure) {
                PerformanceLog.elapsed("EARLY_EVENT_TO_" + claim.channel().name(), false, failure, point);
                // A lost provider response cannot safely be retried without provider idempotency support.
                ledger.finished(claim, "UNCERTAIN");
            }
        }
    }
}
