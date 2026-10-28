package com.omniguardy.backend.domain.detection.infrastructure.fastapi;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto.VisionApiResponse;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VisionFastApiAdapterTest {
    private final VisionFastApiAdapter adapter = new VisionFastApiAdapter();
    private final MediaFile file = new MediaFile("video.mp4", "video/mp4", new byte[]{1});
    private final VideoTriggerContext context = new VideoTriggerContext(
            "event", "original-trigger", TriggerType.AUDIO, "event", "2026-09-23T12:00:00+09:00");
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(adapter, "restClient", builder.build());
        ReflectionTestUtils.setField(adapter, "baseUrl", "http://vision.test");
        ReflectionTestUtils.setField(adapter, "endpoint", "/analyze/vision");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", """
            {"triggerId":"different", "triggerType":"INVALID", "securityEventId":"other",
             "triggeredAt":"2020-01-01T00:00:00Z"}
            """})
    void usesContextWhilePreservingAnalysisResults(String trigger) {
        respond("""
                {"status":"success","result":{"trigger":%s,"analyzedAt":"2026-09-23T12:01:00+09:00",
                  "behavior":{"prediction":"A18","label":"test","confidence":0.9,
                              "classProbabilities":{"A18":0.9}},
                  "video":{"fileName":"video.mp4","durationSeconds":3.0,"fps":30.0,"frameCount":90},
                  "observations":{"trackedPersonCount":2,"hasTracking":true,"hasPose":true},
                  "visionEvents":[{"eventType":"SILENT_SIGNAL","confidence":0.8,"detectedFrame":5,"details":{}}]}}
                """.formatted(trigger));

        VisionAnalysis analysis = adapter.analyze(file, context);

        assertEquals(context.triggerId(), analysis.triggerId());
        assertEquals(context.triggerType(), analysis.triggerType());
        assertEquals(context.securityEventId(), analysis.securityEventId());
        assertEquals(OffsetDateTime.parse(context.triggeredAt()), analysis.triggeredAt());
        assertEquals(OffsetDateTime.parse("2026-09-23T12:01:00+09:00").toInstant(),
                analysis.analyzedAt().toInstant());
        assertEquals("A18", analysis.prediction());
        assertEquals("test", analysis.behavior().label());
        assertEquals(0.9, analysis.confidence());
        assertEquals(Map.of("A18", 0.9), analysis.classProbabilities());
        assertEquals(3.0, analysis.videoDurationSeconds());
        assertEquals(2, analysis.personCount());
        assertTrue(analysis.hasSilentSignal());
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void preservesOptionalKeypadFields(String triggeredAt) {
        respond("{\"status\":\"success\",\"result\":{}}");
        VisionAnalysis analysis = adapter.analyze(file,
                new VideoTriggerContext(null, "keypad", TriggerType.KEYPAD, null, triggeredAt));
        assertEquals("keypad", analysis.triggerId());
        assertEquals(TriggerType.KEYPAD, analysis.triggerType());
        assertNull(analysis.securityEventId());
        assertNull(analysis.triggeredAt());
        assertNull(analysis.behavior());
        assertFalse(analysis.hasVisionEvents());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{\"result\":{}}", "{\"result\":null}",
            "{\"result\":{\"analyzedAt\":\"invalid\"}}", "{\"result\":{\"visionEvents\":[null]}}"})
    void convertsInvalidResponsesToBusinessException(String response) {
        respond(response);
        BusinessException exception = assertThrows(BusinessException.class, () -> adapter.analyze(file, context));
        assertEquals(DetectionErrorCode.VISION_ANALYSIS_FAILED, exception.getErrorCode());
        server.verify();
    }

    @Test
    void convertsInvalidContextTimestampToBusinessException() {
        respond("{\"status\":\"success\",\"result\":{}}");
        BusinessException exception = assertThrows(BusinessException.class, () -> adapter.analyze(file,
                new VideoTriggerContext(null, "keypad", TriggerType.KEYPAD, null, "invalid")));
        assertEquals(DetectionErrorCode.VISION_ANALYSIS_FAILED, exception.getErrorCode());
        assertNotNull(exception.getCause());
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void sendsKeypadMultipartWithoutSecurityEventId(String securityEventId) {
        VideoTriggerContext keypad = new VideoTriggerContext(
                null, "keypad-trigger", TriggerType.KEYPAD, securityEventId, "2026-09-24T12:00:00+09:00");
        expectMultipart(Map.of(
                "triggerId", "keypad-trigger",
                "triggerType", "KEYPAD",
                "triggeredAt", "2026-09-24T12:00:00+09:00"));

        adapter.analyze(file, keypad);

        server.verify();
    }

    @Test
    void sendsAudioMultipartWithSecurityEventId() {
        expectMultipart(Map.of(
                "triggerId", "original-trigger",
                "triggerType", "AUDIO",
                "triggeredAt", "2026-09-23T12:00:00+09:00",
                "securityEventId", "event"));

        adapter.analyze(file, context);

        server.verify();
    }

    private void expectMultipart(Map<String, String> fields) {
        server.expect(requestTo("http://vision.test/analyze/vision"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MediaType contentType = request.getHeaders().getContentType();
                    assertNotNull(contentType);
                    assertTrue(MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType));
                    String boundary = contentType.getParameter("boundary");
                    assertNotNull(boundary);
                    // ISO-8859-1 preserves every byte, including the binary file part.
                    String body = new String(((MockClientHttpRequest) request).getBodyAsBytes(),
                            StandardCharsets.ISO_8859_1);
                    String[] parts = body.split(Pattern.quote("--" + boundary), -1);
                    assertEquals(fields.size() + 3, parts.length);
                    assertEquals("", parts[0]);
                    assertEquals("--\r\n", parts[parts.length - 1]);
                    fields.forEach((name, value) -> assertMultipartPart(body, boundary,
                            "name=\"" + name + "\"", value));
                    if (!fields.containsKey("securityEventId")) {
                        assertFalse(body.contains("name=\"securityEventId\""));
                    }
                    assertMultipartPart(body, boundary,
                            "name=\"file\"; filename=\"video.mp4\"",
                            new String(file.bytes(), StandardCharsets.ISO_8859_1));
                })
                .andRespond(withSuccess("{\"status\":\"success\",\"result\":{}}", MediaType.APPLICATION_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"N1", "A18", "A21", "A17", "A19", "FUTURE_CLASS"})
    void mapsAudioBehaviorWithoutRestrictingPrediction(String prediction) {
        respond(successResponse("AUDIO", "\"event\"", """
                {"prediction":"%s","label":"repeated_door_knocking","confidence":0.91,
                 "classProbabilities":{"N1":0.03,"A17":0.01,"A18":0.02,"A19":0.03,"A20":0.0,"A21":0.91}}
                """.formatted(prediction), "[]"));

        VisionAnalysis analysis = adapter.analyze(file, context);

        assertEquals(new VisionBehavior(prediction, "repeated_door_knocking", 0.91,
                Map.of("N1", 0.03, "A17", 0.01, "A18", 0.02, "A19", 0.03, "A20", 0.0, "A21", 0.91)), analysis.behavior());
        assertEquals(TriggerType.AUDIO, analysis.triggerType());
        assertEquals("event", analysis.securityEventId());
        assertTrue(analysis.visionEvents().isEmpty());
        assertCommonResponseFields(analysis);
        var serialized = JsonMapper.builder().build().valueToTree(analysis.behavior());
        assertEquals("repeated_door_knocking", serialized.get("label").asText());
        assertFalse(serialized.has("behaviorName"));
        server.verify();
    }

    @Test
    void mapsNormalKeypadWithNullBehaviorAndEmptyEvents() {
        respond(successResponse("KEYPAD", "null", "null", "[]"));

        VisionAnalysis analysis = adapter.analyze(file, keypadContext());

        assertKeypadResponse(analysis);
        assertEquals(List.of(), analysis.visionEvents());
        server.verify();
    }

    @Test
    void mapsSilentSignalDetails() {
        respond(successResponse("KEYPAD", "null", "null", """
                [{"eventType":"SILENT_SIGNAL","confidence":0.94,"detectedFrame":156,
                  "details":{"signalCode":"S1","signalName":"emergency_hand_gesture"}}]
                """));

        VisionAnalysis analysis = adapter.analyze(file, keypadContext());

        assertKeypadResponse(analysis);
        assertEquals(List.of(new VisionEvent("SILENT_SIGNAL", 0.94, 156,
                Map.of("signalCode", "S1", "signalName", "emergency_hand_gesture"))), analysis.visionEvents());
        server.verify();
    }

    @Test
    void mapsExhibitionRulesWithNullableConfidenceAndPreservesDetails() {
        respond("""
                {"status":"success","result":{
                  "analyzedAt":"2026-09-23T12:00:10+09:00",
                  "behavior":null,
                  "visionEvents":[
                    {"eventType":"PERSON_DWELL_OBSERVED","confidence":null,"detectedFrame":120,
                     "details":{"trackId":7,"thresholdSeconds":3.0,"cumulativeObservedSeconds":5.1,
                       "continuousObservedSeconds":4.2,"firstLastSpanSeconds":5.4,"maxDetectionGapSeconds":0.4,
                       "identityContinuity":true,"windowSeconds":10.0,"experimental":true}},
                    {"eventType":"CAMERA_OCCLUSION_SUSPECTED","confidence":null,"detectedFrame":180,
                     "details":{"startSeconds":4.0,"endSeconds":7.2,"durationSeconds":3.2,
                       "edgeDensityThreshold":0.1,"laplacianThreshold":25.0,"minSustainedSeconds":2.0,
                       "windowSeconds":10.0,"experimental":true}}],
                  "observations":{"trackedPersonCount":1,"hasTracking":true,"hasPose":false,
                    "analysisMode":"exhibition_rules"}}}
                """);

        VisionAnalysis analysis = adapter.analyze(file, context);

        assertEquals("exhibition_rules", analysis.analysisMode());
        assertNull(analysis.behavior());
        assertEquals(2, analysis.visionEvents().size());
        assertNull(analysis.visionEvents().get(0).confidence());
        assertEquals(120, analysis.visionEvents().get(0).detectedFrame());
        assertEquals(9, analysis.visionEvents().get(0).details().size());
        assertEquals(7, analysis.visionEvents().get(0).details().get("trackId"));
        assertEquals(8, analysis.visionEvents().get(1).details().size());
        assertEquals(3.2, analysis.visionEvents().get(1).details().get("durationSeconds"));
        assertTrue(analysis.requiresAudioRiskAssessment());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"REAR_CLOSE_APPROACH_SUSPECTED", "FUTURE_EVENT"})
    void preservesFlexibleEventTypesAndDetails(String eventType) {
        respond(successResponse("KEYPAD", "null", "null", """
                [{"eventType":"%s","confidence":0.82,"detectedFrame":184,
                  "details":{"personCount":2,"delayedEntrySeconds":2.1,
                    "distanceClosingDetected":true,"closeProximityDurationSeconds":2.8,
                    "nested":{"name":"example","enabled":false,"values":[1,"two",true]}}}]
                """.formatted(eventType)));

        VisionAnalysis analysis = adapter.analyze(file, keypadContext());

        assertKeypadResponse(analysis);
        assertEquals(List.of(new VisionEvent(eventType, 0.82, 184, Map.of(
                "personCount", 2, "delayedEntrySeconds", 2.1,
                "distanceClosingDetected", true, "closeProximityDurationSeconds", 2.8,
                "nested", Map.of("name", "example", "enabled", false,
                        "values", List.of(1, "two", true))))), analysis.visionEvents());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUDIO", "KEYPAD"})
    void deserializesResponseTriggerAndOffsetDateTimes(String triggerType) {
        String securityEventId = "AUDIO".equals(triggerType) ? "\"event\"" : "null";
        VisionApiResponse response = JsonMapper.builder().build().readValue(
                successResponse(triggerType, securityEventId, "null", "[]"), VisionApiResponse.class);

        assertEquals("success", response.status());
        assertEquals("response-trigger", response.result().trigger().triggerId());
        assertEquals(triggerType, response.result().trigger().triggerType());
        assertEquals("AUDIO".equals(triggerType) ? "event" : null,
                response.result().trigger().securityEventId());
        assertEquals(OffsetDateTime.parse("2026-09-23T12:00:00+09:00").toInstant(),
                response.result().trigger().triggeredAt().toInstant());
        assertEquals(OffsetDateTime.parse("2026-09-23T12:00:10+09:00").toInstant(),
                response.result().analyzedAt().toInstant());
    }

    private VideoTriggerContext keypadContext() {
        return new VideoTriggerContext(null, "keypad-trigger", TriggerType.KEYPAD, null, context.triggeredAt());
    }

    private void assertKeypadResponse(VisionAnalysis analysis) {
        assertEquals(TriggerType.KEYPAD, analysis.triggerType());
        assertNull(analysis.securityEventId());
        assertNull(analysis.behavior());
        assertCommonResponseFields(analysis);
    }

    private void assertCommonResponseFields(VisionAnalysis analysis) {
        assertEquals(new VisionVideoInfo("door_video.mp4", 10.0, 30.0, 300), analysis.video());
        assertEquals(new VisionObservations(2, true, false), analysis.observations());
        assertEquals(OffsetDateTime.parse(context.triggeredAt()), analysis.triggeredAt());
        assertEquals(OffsetDateTime.parse("2026-09-23T12:00:10+09:00").toInstant(),
                analysis.analyzedAt().toInstant());
    }

    private String successResponse(String triggerType, String securityEventId, String behavior, String events) {
        return """
                {"status":"success","result":{
                  "trigger":{"triggerId":"response-trigger","triggerType":"%s",
                    "securityEventId":%s,"triggeredAt":"2026-09-23T12:00:00+09:00"},
                  "analyzedAt":"2026-09-23T12:00:10+09:00",
                  "video":{"fileName":"door_video.mp4","durationSeconds":10.0,"fps":30.0,"frameCount":300},
                  "behavior":%s,"visionEvents":%s,
                  "observations":{"trackedPersonCount":2,"hasTracking":true,"hasPose":false}}}
                """.formatted(triggerType, securityEventId, behavior, events);
    }

    private void assertMultipartPart(String body, String boundary, String disposition, String value) {
        String pattern = Pattern.quote("--" + boundary + "\r\nContent-Disposition: form-data; "
                + disposition + "\r\n")
                + "(?:[^\\r\\n]+\\r\\n)*\\r\\n"
                + Pattern.quote(value + "\r\n--" + boundary);
        assertTrue(Pattern.compile(pattern).matcher(body).find(), "Missing or incorrect multipart part: " + disposition);
    }

    private void respond(String response) {
        server.expect(requestTo("http://vision.test/analyze/vision"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
}
