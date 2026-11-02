package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.visionsession.domain.error.StreamingVisionException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.infrastructure.streaming.StreamingVisionJsonMapper;
import org.junit.jupiter.api.Test;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class StreamingVisionFinalResultTest {
    StreamingVisionJsonMapper mapper = new StreamingVisionJsonMapper();

    @Test void bothTriggerTypesRequireTheirOwnCompleteSuccessConditions() {
        for (String type : new String[]{"KEYPAD", "AUDIO"}) {
            String event = type.equals("AUDIO") ? "event" : null;
            var expected = new VisionBinding("session", VisionTriggerType.valueOf(type), "trigger", event);
            String json = resultJson("session", "trigger", type, event);
            assertEquals(VisionFinalOutcome.VALID, mapper.result(json).evaluate(expected));
            assertEquals(VisionFinalOutcome.REJECTED, mapper.result(json.replace("COMPLETE_" + type,
                    type.equals("KEYPAD") ? "COMPLETE_AUDIO" : "COMPLETE_KEYPAD")).evaluate(expected));
            assertEquals(VisionFinalOutcome.PARTIAL, mapper.result(json.replace("COMPLETE_" + type, "PARTIAL_SAMPLED_TIME_AXIS")).evaluate(expected));
            assertEquals(VisionFinalOutcome.PARTIAL, mapper.result(json.replace("\"status\":\"success\"", "\"status\":\"partial\"")).evaluate(expected));
            assertEquals(VisionFinalOutcome.REJECTED, mapper.result(json.replace("COMPLETED", "PROCESSING")).evaluate(expected));
            assertEquals(VisionFinalOutcome.REJECTED, mapper.result(json.replace("\"error\":null", "\"error\":\"inconsistent\"")).evaluate(expected));
        }
    }
    @Test void everyBindingFieldIsComparedExactly() {
        var json = resultJson("session", "trigger", "AUDIO", "event");
        var expected = new VisionBinding("session", VisionTriggerType.AUDIO, "trigger", "event");
        for (String changed : new String[]{json.replace("\"session\"", "\"Session\""), json.replace("\"trigger\",", "\"other\","),
                json.replace("\"AUDIO\"", "\"KEYPAD\""), json.replace("\"securityEventId\":\"event\"", "\"securityEventId\":null")}) {
            assertEquals(VisionFinalOutcome.REJECTED, mapper.result(changed).evaluate(expected));
        }
    }
    @Test void nullableCountAndArbitraryEventsAndEarlyReferencesArePreserved() {
        var json = resultJson("session", "trigger", "KEYPAD", null)
                .replace("\"trackedPersonCount\":0", "\"trackedPersonCount\":null")
                .replace("\"visionEvents\":[]", "\"visionEvents\":[{\"eventType\":\"FUTURE\",\"arbitrary\":{\"score\":0.8}}]");
        var result = mapper.result(json);
        assertNull(result.trackedPersonCount()); assertTrue(result.visionEventsJson().contains("arbitrary"));
        assertEquals("[\"signal-001\"]", result.relatedEarlyEventInstanceIdsJson());
        assertEquals(VisionFinalOutcome.VALID, result.evaluate(result.binding()));
    }
    @Test void incorrectTypesAndNonObjectsAreNotCoerced() {
        String json = resultJson("session", "trigger", "KEYPAD", null);
        for (String invalid : new String[]{json.replace("\"trackedPersonCount\":0", "\"trackedPersonCount\":\"0\""),
                json.replace("\"trackedPersonCount\":0", "\"trackedPersonCount\":0.2"), json.replace("\"visionEvents\":[]", "\"visionEvents\":[1]"),
                json.replace("[\"signal-001\"]", "[1]"), json.replace("2026-11-02T14:49:01.623+09:00", "not-a-time"),
                json.replace("\"status\":\"success\"", "\"status\":\"other\""), json.replace("\"result\":{", "\"result\":[{")}) {
            assertEquals("INVALID_RESPONSE", assertThrows(StreamingVisionException.class, () -> mapper.result(invalid)).code());
        }
    }
}
