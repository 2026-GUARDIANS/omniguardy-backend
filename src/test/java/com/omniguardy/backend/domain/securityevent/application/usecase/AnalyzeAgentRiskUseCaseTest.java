package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.securityevent.application.metrics.AgentPerformanceMetrics;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.application.port.out.RiskAssessmentPort;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentContext;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.infrastructure.agent.LangChainRiskAssessmentAdapter;
import com.omniguardy.backend.domain.securityevent.infrastructure.agent.RiskAssessmentAiService;
import com.omniguardy.backend.domain.securityevent.infrastructure.agent.AgentStructuredResponse;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyzeAgentRiskUseCaseTest {
    private final SecurityEventRepository repository = mock(SecurityEventRepository.class);
    private final RiskAssessmentPort port = mock(RiskAssessmentPort.class);
    private final RiskResponseService riskResponseService = mock(RiskResponseService.class);
    private final AgentPerformanceMetrics performanceMetrics = new AgentPerformanceMetrics();
    private final AnalyzeAgentRiskUseCase useCase = new AnalyzeAgentRiskUseCase(
            repository, port, new ObjectMapper(), riskResponseService, performanceMetrics);

    @Test void includesAudioAndAllVisionDataAndCompletesExistingEvent() {
        SecurityEvent event = visionAnalyzedEvent();
        when(repository.findByEventId("event-1")).thenReturn(Optional.of(event));
        when(port.assess(any())).thenReturn(new AgentAssessment(RiskLevel.HIGH, 85, "audio? vision 媛믪뿉 洹쇨굅"));
        useCase.analyze("event-1");
        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(port).assess(captor.capture());
        assertEquals(EventTriggerType.AUDIO, captor.getValue().triggerType());
        assertTrue(captor.getValue().vision().visionEvents().isEmpty());
        assertEquals(AudioEventType.KNOCK, captor.getValue().audio().eventType());
        assertEquals(0.999342, captor.getValue().audio().probability());
        assertEquals("A18", captor.getValue().vision().prediction());
        assertEquals(0.464551, captor.getValue().vision().confidence());
        assertEquals(0.222806, captor.getValue().vision().classProbabilities().get("A17"));
        assertEquals(0, captor.getValue().vision().personCount());
        assertEquals("MEDIUM", event.getRiskLevel());
        assertEquals(85, event.getRiskScore());
        assertEquals("COMPLETED", event.getStatus());
        verify(riskResponseService).respond(event, RiskLevel.MEDIUM);
        var order = inOrder(repository, riskResponseService);
        order.verify(repository).save(event);
        order.verify(riskResponseService).respond(event, RiskLevel.MEDIUM);
        assertEquals(1, performanceMetrics.snapshot().agentExecutionCount());
        assertEquals(0, performanceMetrics.snapshot().agentFailureCount());
    }

    @Test void failurePreservesEvidenceAndMarksFailed() {
        SecurityEvent event = visionAnalyzedEvent();
        when(repository.findByEventId("event-1")).thenReturn(Optional.of(event));
        when(port.assess(any())).thenThrow(new IllegalStateException("OpenAI failure"));
        assertThrows(IllegalStateException.class, () -> useCase.analyze("event-1"));
        assertEquals(AudioEventType.KNOCK, event.getAudioEventType());
        assertEquals("A18", event.getVisionPrediction());
        assertEquals("FAILED", event.getStatus());
        verify(repository).save(event);
        verifyNoInteractions(riskResponseService);
        assertEquals(1, performanceMetrics.snapshot().agentExecutionCount());
        assertEquals(1, performanceMetrics.snapshot().agentFailureCount());
    }

    @Test void audioA21IsAlwaysLowAndPreservesAgentScore() {
        SecurityEvent event = SecurityEvent.builder().eventId("a21-event")
                .triggerType(EventTriggerType.AUDIO).status("VISION_ANALYZED")
                .visionPrediction("A21").visionEvents("[]").build();
        when(repository.findByEventId("a21-event")).thenReturn(Optional.of(event));
        when(port.assess(any())).thenReturn(new AgentAssessment(RiskLevel.HIGH, 24, "반복 노크 감지"));

        AgentAssessment result = useCase.analyze("a21-event");

        assertEquals(RiskLevel.LOW, result.riskLevel());
        assertEquals(24, result.riskScore());
        assertEquals("LOW", event.getRiskLevel());
        assertEquals(24, event.getRiskScore());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"A18, MEDIUM", "A21, LOW"})
    void explicitTemporalTrial6KeepsExistingRiskPolicy(String prediction, RiskLevel expected) {
        SecurityEvent event = SecurityEvent.builder().eventId("temporal-" + prediction)
                .triggerType(EventTriggerType.AUDIO).status("VISION_ANALYZED")
                .visionAnalysisMode("temporal_trial6").visionPrediction(prediction).visionEvents("[]").build();
        when(repository.findByEventId(event.getEventId())).thenReturn(Optional.of(event));
        when(port.assess(any())).thenReturn(new AgentAssessment(RiskLevel.HIGH, 30, "temporal evidence"));

        AgentAssessment result = useCase.analyze(event.getEventId());

        assertEquals(expected, result.riskLevel());
        verify(riskResponseService).respond(event, expected);
    }

    private SecurityEvent visionAnalyzedEvent() {
        SecurityEvent event = SecurityEvent.builder().eventId("event-1").triggerType(EventTriggerType.AUDIO)
                .audioEventType(AudioEventType.KNOCK)
                .audioProbability(0.999342).status("CAMERA_REQUESTED").build();
        event.updateVisionResult("A18", 0.464551, "{\"N1\":0.035310,\"A17\":0.222806}",
                "[]", 0, 10.0, java.time.OffsetDateTime.parse("2026-09-11T00:00:00Z"));
        return event;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "[]", "null"})
    void missingVisionEventsBecomeEmptyArray(String json) {
        SecurityEvent event = SecurityEvent.builder().eventId("legacy").triggerType(EventTriggerType.AUDIO)
                .status("VISION_ANALYZED").visionPrediction("A18").visionEvents(json).build();
        when(repository.findByEventId("legacy")).thenReturn(Optional.of(event));
        when(port.assess(any())).thenReturn(new AgentAssessment(RiskLevel.LOW, 0, "No evidence"));
        useCase.analyze("legacy");
        var captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(port).assess(captor.capture());
        assertTrue(captor.getValue().vision().visionEvents().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-json", "{}"})
    void malformedVisionEventsDoNotReachAgent(String json) {
        SecurityEvent event = SecurityEvent.builder().eventId("invalid").status("VISION_ANALYZED")
                .visionEvents(json).build();
        when(repository.findByEventId("invalid")).thenReturn(Optional.of(event));
        assertThrows(BusinessException.class, () -> useCase.analyze("invalid"));
        verifyNoInteractions(port);
        assertEquals("FAILED", event.getStatus());
    }

    @Test void keypadEventsReachAiServiceAsStructuredArrayWithAllFields() {
        String events = """
                [{"eventType":"REAR_CLOSE_APPROACH_SUSPECTED","confidence":0.9,"detectedFrame":20,
                  "details":{"label":"entry","count":2,"nested":{"visible":true}}}]
                """;
        SecurityEvent event = SecurityEvent.builder().eventId("keypad-event").triggerType(EventTriggerType.KEYPAD)
                .status("VISION_ANALYZED").visionEvents(events).personCount(1).build();
        when(repository.findByEventId("keypad-event")).thenReturn(Optional.of(event));
        RiskAssessmentAiService service = mock(RiskAssessmentAiService.class);
        AgentStructuredResponse response = new AgentStructuredResponse();
        response.riskLevel = "LOW";
        response.riskScore = 10;
        response.reason = "Vision evidence";
        when(service.assess(anyString())).thenReturn(response);
        ObjectMapper mapper = new ObjectMapper();
        new AnalyzeAgentRiskUseCase(repository,
                new LangChainRiskAssessmentAdapter(service, mapper, performanceMetrics), mapper,
                riskResponseService, performanceMetrics)
                .analyze("keypad-event");
        var captor = ArgumentCaptor.forClass(String.class);
        verify(service).assess(captor.capture());
        var json = mapper.readTree(captor.getValue());
        assertEquals("KEYPAD", json.get("triggerType").asString());
        assertTrue(json.get("audio").get("eventType").isNull());
        assertEquals(0.0, json.get("audio").get("probability").asDouble());
        var actualEvents = json.get("vision").get("visionEvents");
        assertTrue(actualEvents.isArray());
        assertEquals(mapper.readTree(events), actualEvents);
        assertEquals("HIGH", event.getRiskLevel());
        assertEquals(10, event.getRiskScore());
        assertFalse(json.has("triggerId"));
        assertFalse(json.has("triggeredAt"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "PERSON_DWELL_OBSERVED, LOW",
            "CAMERA_OCCLUSION_SUSPECTED, MEDIUM"
    })
    void exhibitionEventDeterminesRequiredRiskLevel(String eventType, RiskLevel expected) throws Exception {
        String events = """
                [{"eventType":"%s","confidence":null,"detectedFrame":30,
                  "details":{"windowSeconds":10.0,"experimental":true}}]
                """.formatted(eventType);
        SecurityEvent event = exhibitionEvent("exhibition-" + eventType, events);
        when(repository.findByEventId(event.getEventId())).thenReturn(Optional.of(event));
        when(port.assess(any())).thenReturn(new AgentAssessment(RiskLevel.HIGH, 40, "event evidence"));

        AgentAssessment result = useCase.analyze(event.getEventId());

        assertEquals(expected, result.riskLevel());
        assertEquals(expected.name(), event.getRiskLevel());
        verify(riskResponseService).respond(event, expected);
        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(port).assess(captor.capture());
        assertEquals("exhibition_rules", captor.getValue().vision().analysisMode());
        assertNull(captor.getValue().vision().confidence());
        assertNull(captor.getValue().vision().visionEvents().get(0).confidence());
    }

    @Test void exhibitionOcclusionWinsRegardlessOfEventOrder() throws Exception {
        for (List<String> order : List.of(
                List.of("PERSON_DWELL_OBSERVED", "CAMERA_OCCLUSION_SUSPECTED"),
                List.of("CAMERA_OCCLUSION_SUSPECTED", "PERSON_DWELL_OBSERVED"))) {
            reset(repository, port, riskResponseService);
            String eventId = "both-" + order.get(0);
            String events = new ObjectMapper().writeValueAsString(order.stream()
                    .map(type -> Map.of("eventType", type, "detectedFrame", 1, "details", Map.of()))
                    .toList());
            SecurityEvent event = exhibitionEvent(eventId, events);
            when(repository.findByEventId(eventId)).thenReturn(Optional.of(event));
            when(port.assess(any())).thenReturn(new AgentAssessment(RiskLevel.LOW, 50, "both events"));

            assertEquals(RiskLevel.MEDIUM, useCase.analyze(eventId).riskLevel());
            verify(riskResponseService).respond(event, RiskLevel.MEDIUM);
        }
    }

    @Test void unknownAudioAnalysisModeDoesNotReachAgent() {
        SecurityEvent event = SecurityEvent.builder().eventId("unknown-mode")
                .triggerType(EventTriggerType.AUDIO).status("VISION_ANALYZED")
                .visionAnalysisMode("unknown_mode").visionEvents("[]").build();
        when(repository.findByEventId("unknown-mode")).thenReturn(Optional.of(event));

        assertThrows(BusinessException.class, () -> useCase.analyze("unknown-mode"));

        verifyNoInteractions(port, riskResponseService);
        assertEquals("FAILED", event.getStatus());
    }

    private SecurityEvent exhibitionEvent(String eventId, String events) {
        return SecurityEvent.builder().eventId(eventId).triggerType(EventTriggerType.AUDIO)
                .status("VISION_ANALYZED").visionAnalysisMode("exhibition_rules")
                .visionEvents(events).classProbabilities("{}").build();
    }
}

