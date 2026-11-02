package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingAgentInputException;
import com.omniguardy.backend.domain.visionsession.infrastructure.agent.StreamingAgentVisionMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class StreamingAgentMappingTest {
    StreamingAgentVisionMapper mapper = new StreamingAgentVisionMapper();
    StreamingAgentSource source(String json) {
        return new StreamingAgentSource(new VisionBinding("session", VisionTriggerType.KEYPAD, "trigger", null),
                OffsetDateTime.parse("2026-11-02T00:00:00Z"), json);
    }
    String json(String events) { return resultJson("session", "trigger", "KEYPAD", null).replace("\"visionEvents\":[]", "\"visionEvents\":" + events); }

    @Test void mapsOnlyRealEvidenceAndPreservesAllStreamingFields() {
        var vision = mapper.map(source(json("""
                [{"eventType":"SILENT_SIGNAL","detectedFrame":114,"confirmedFrame":115,"capturedAt":"3.8",
                  "details":{"label":"visible"}}]
                """)));
        assertNull(vision.prediction()); assertNull(vision.confidence()); assertTrue(vision.classProbabilities().isEmpty());
        assertNull(vision.behavior()); assertNull(vision.video());
        assertEquals(0, vision.personCount()); assertEquals("exhibition_rules", vision.analysisMode());
        assertTrue(vision.hasHighRiskKeypadEvent());
        var event = vision.visionEvents().getFirst(); assertNull(event.confidence()); assertEquals(114, event.detectedFrame());
        var details = new ObjectMapper().valueToTree(event.details());
        assertEquals(115, details.get("streamingEvent").get("confirmedFrame").intValue());
        assertEquals("3.8", details.get("streamingEvent").get("capturedAt").stringValue());
        assertEquals("visible", details.get("label").stringValue());
    }
    @Test void nullIsNeverConvertedToObservedZeroEvenForEmptyEvents() {
        for (String events : new String[]{"[]", "[{\"eventType\":\"SILENT_SIGNAL\",\"detectedFrame\":1}]"}) {
            var failure = assertThrows(StreamingAgentInputException.class,
                    () -> mapper.map(source(json(events).replace("\"trackedPersonCount\":0", "\"trackedPersonCount\":null"))));
            assertEquals("UNKNOWN_PERSON_COUNT", failure.getMessage());
        }
    }
    @Test void zeroAndPositiveUniqueTrackedPersonCountsKeepTheirMeaning() {
        for (int count : new int[]{0, 1, 5}) {
            var vision = mapper.map(source(json("[]").replace("\"trackedPersonCount\":0", "\"trackedPersonCount\":" + count)));
            assertEquals(count, vision.personCount());
            assertEquals(count, vision.observations().trackedPersonCount());
        }
    }
    @Test void partialFailedAndBindingMismatchNeverMapToAgentEvidence() {
        for (String invalid : new String[]{json("[]").replace("COMPLETE_KEYPAD", "PARTIAL_SAMPLED_TIME_AXIS"),
                json("[]").replace("COMPLETED", "FAILED"), json("[]").replace("\"session\"", "\"other\"")}) {
            assertThrows(StreamingAgentInputException.class, () -> mapper.map(source(invalid)));
        }
    }
    @Test void unknownEventsAndOutOfRangeLegacyFramesAreBlockedRatherThanInventingMappings() {
        for (String events : new String[]{"[{\"eventType\":\"NEW_EVENT\"}]",
                "[{\"eventType\":\"SILENT_SIGNAL\",\"detectedFrame\":2147483648}]"}) {
            assertThrows(StreamingAgentInputException.class, () -> mapper.map(source(json(events))));
        }
    }
}
