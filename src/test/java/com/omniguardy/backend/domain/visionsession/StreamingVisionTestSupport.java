package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.streaming.StreamingVisionJsonMapper;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamingVisionTestSupport {
    private StreamingVisionTestSupport() {}
    public static String resultJson(String session, String trigger, String type, String event) {
        return """
                {"messageType":"FINAL_RESULT","sessionId":"%s",
                 "trigger":{"triggerId":"%s","triggerType":"%s","securityEventId":%s},
                 "analysisState":"COMPLETED","analysisCompleteness":"COMPLETE_%s",
                 "relatedEarlyEventInstanceIds":["signal-001"],"status":"success",
                 "result":{"visionEvents":[],"trackedPersonCount":0,"streamingPocScope":"PoC scope"},
                 "error":null,"emittedAt":"2026-11-02T14:49:01.623+09:00"}
                """.formatted(session, trigger, type, event == null ? "null" : "\"" + event + "\"", type);
    }
    public static String statusJson(String session, String trigger, String type, String event) {
        return """
                {"sessionId":"%s","triggerId":"%s","triggerType":"%s","securityEventId":%s,
                 "analysisState":"COMPLETED","analysisCompleteness":"COMPLETE_%s",
                 "inputComplete":true,"finalResultAvailable":true,"error":null}
                """.formatted(session, trigger, type, event == null ? "null" : "\"" + event + "\"", type);
    }
    public static StreamingVisionFinalResult result(String session, String trigger, String type, String event) {
        return new StreamingVisionJsonMapper().result(resultJson(session, trigger, type, event));
    }
    public static StreamingVisionStatus status(String session, String trigger, String type, String event) {
        return new StreamingVisionJsonMapper().status(statusJson(session, trigger, type, event));
    }
    public static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-02T00:00:00Z"));
        public void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
