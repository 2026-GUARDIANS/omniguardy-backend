package com.omniguardy.backend.domain.visionsession.infrastructure.agent;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingAgentMappingPort;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingAgentInputException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.streaming.StreamingVisionJsonMapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class StreamingAgentVisionMapper implements StreamingAgentMappingPort {
    private final StreamingVisionJsonMapper streaming = new StreamingVisionJsonMapper();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public VisionAnalysis map(StreamingAgentSource source) {
        try {
            var result = streaming.result(source.rawResultJson());
            if (result.evaluate(source.binding()) != VisionFinalOutcome.VALID) throw invalid("INELIGIBLE_FINAL_RESULT");
            var events = new ArrayList<VisionEvent>();
            for (var raw : mapper.readTree(result.visionEventsJson())) {
                var type = raw.get("eventType");
                if (type == null || !type.isString()) throw invalid("UNSUPPORTED_EVENT");
                Integer frame = null;
                var value = raw.get("detectedFrame");
                if (value != null && !value.isNull()) {
                    if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid("UNSUPPORTED_FRAME_RANGE");
                    frame = value.intValue();
                }
                Double confidence = null;
                value = raw.get("confidence");
                if (value != null && !value.isNull()) {
                    if (!value.isNumber() || !Double.isFinite(value.doubleValue())) throw invalid("INVALID_EVENT_CONFIDENCE");
                    confidence = value.doubleValue();
                }
                Map<String, Object> details = new LinkedHashMap<>();
                var nativeDetails = raw.get("details");
                if (nativeDetails != null && !nativeDetails.isNull()) {
                    if (!nativeDetails.isObject()) throw invalid("INVALID_EVENT_DETAILS");
                    details.putAll(mapper.convertValue(nativeDetails, new TypeReference<Map<String, Object>>() {}));
                }
                // Preserve every original field, including confirmedFrame/capturedAt and future details.
                details.put("streamingEvent", mapper.convertValue(raw, new TypeReference<Map<String, Object>>() {}));
                var event = new VisionEvent(type.stringValue(), confidence, frame, details);
                boolean supported = source.binding().triggerType() == VisionTriggerType.KEYPAD
                        ? event.isHighRiskKeypadEvent() : event.isExhibitionRulesEvent();
                if (!supported) throw invalid("UNSUPPORTED_EVENT");
                events.add(event);
            }
            // The existing Agent contract cannot represent unknown person count. Never turn null into observed zero.
            if (result.trackedPersonCount() == null) throw invalid("UNKNOWN_PERSON_COUNT");
            var type = source.binding().triggerType() == VisionTriggerType.KEYPAD ? TriggerType.KEYPAD : TriggerType.AUDIO;
            return new VisionAnalysis(source.binding().triggerId(), type, source.binding().securityEventId(), source.triggeredAt(),
                    OffsetDateTime.parse(result.emittedAt()), null, null, events,
                    new VisionObservations(result.trackedPersonCount(), null, null, VisionAnalysis.EXHIBITION_RULES));
        } catch (StreamingAgentInputException exception) { throw exception; }
        catch (RuntimeException exception) { throw invalid("INVALID_STREAMING_INPUT"); }
    }
    private static StreamingAgentInputException invalid(String reason) { return new StreamingAgentInputException(reason); }
}
