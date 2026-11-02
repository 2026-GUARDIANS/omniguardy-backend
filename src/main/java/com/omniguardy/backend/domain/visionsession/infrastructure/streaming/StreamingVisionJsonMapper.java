package com.omniguardy.backend.domain.visionsession.infrastructure.streaming;

import com.omniguardy.backend.domain.visionsession.domain.error.StreamingVisionException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;

/** Only the streaming boundary uses this strict parser; heterogeneous event objects are retained verbatim. */
public class StreamingVisionJsonMapper {
    private final ObjectMapper mapper = new ObjectMapper();

    public StreamingVisionStatus status(String json) {
        try {
            var root = object(mapper.readTree(json));
            return new StreamingVisionStatus(binding(root, root), VisionAnalysisState.valueOf(string(root, "analysisState", false)),
                    VisionAnalysisCompleteness.valueOf(string(root, "analysisCompleteness", false)),
                    bool(root, "inputComplete"), bool(root, "finalResultAvailable"), string(root, "error", true), root.toString());
        } catch (RuntimeException exception) { throw invalid(); }
    }

    public StreamingVisionFinalResult result(String json) {
        try {
            var root = object(mapper.readTree(json));
            if (!"FINAL_RESULT".equals(string(root, "messageType", false))) throw invalid();
            var result = object(root.get("result"));
            var events = result.get("visionEvents");
            var related = root.get("relatedEarlyEventInstanceIds");
            if (events == null || !events.isArray() || related == null || !related.isArray()) throw invalid();
            for (var event : events) if (!event.isObject()) throw invalid();
            for (var id : related) if (!id.isString()) throw invalid();
            var count = result.get("trackedPersonCount");
            Integer personCount = null;
            if (count != null && !count.isNull()) {
                if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0) throw invalid();
                personCount = count.intValue();
            }
            String emittedAt = string(root, "emittedAt", false);
            if (emittedAt.length() > 64) throw invalid();
            OffsetDateTime.parse(emittedAt);
            String status = string(root, "status", false);
            if (!java.util.Set.of("success", "partial", "failed").contains(status)) throw invalid();
            return new StreamingVisionFinalResult(binding(root, object(root.get("trigger"))),
                    VisionAnalysisState.valueOf(string(root, "analysisState", false)),
                    VisionAnalysisCompleteness.valueOf(string(root, "analysisCompleteness", false)), status, personCount,
                    events.toString(), related.toString(), string(result, "streamingPocScope", false),
                    string(root, "error", true), emittedAt, root.toString());
        } catch (RuntimeException exception) { throw invalid(); }
    }

    private static VisionBinding binding(JsonNode root, JsonNode trigger) {
        return new VisionBinding(string(root, "sessionId", false), VisionTriggerType.valueOf(string(trigger, "triggerType", false)),
                string(trigger, "triggerId", false), string(trigger, "securityEventId", true));
    }
    private static JsonNode object(JsonNode value) { if (value == null || !value.isObject()) throw invalid(); return value; }
    private static String string(JsonNode node, String key, boolean nullable) {
        var value = node.get(key);
        if (value == null || value.isNull()) { if (nullable) return null; throw invalid(); }
        if (!value.isString()) throw invalid();
        return value.stringValue();
    }
    private static boolean bool(JsonNode node, String key) {
        var value = node.get(key);
        if (value == null || !value.isBoolean()) throw invalid();
        return value.booleanValue();
    }
    private static StreamingVisionException invalid() { return new StreamingVisionException("INVALID_RESPONSE", false); }
}
