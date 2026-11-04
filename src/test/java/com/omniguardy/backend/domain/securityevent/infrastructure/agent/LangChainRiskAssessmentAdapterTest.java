package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.securityevent.application.metrics.AgentPerformanceMetrics;
import com.omniguardy.backend.global.error.exception.BusinessException;

import com.omniguardy.backend.domain.securityevent.domain.model.AgentContext;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;

import java.util.Map;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class LangChainRiskAssessmentAdapterTest {
    @Test void parsesAndValidatesStructuredOutput() {
        RiskAssessmentAiService service = mock(RiskAssessmentAiService.class);
        AgentStructuredResponse response = new AgentStructuredResponse();
        response.riskLevel = "HIGH";
        response.riskScore = 85;
        response.reason = "KNOCK 0.99 및 A18 0.46을 근거로 판단";
        when(service.assess(contains("classProbabilities"))).thenReturn(Result.<AgentStructuredResponse>builder()
                .content(response).tokenUsage(new TokenUsage(120, 30, 150)).build());
        AgentPerformanceMetrics metrics = new AgentPerformanceMetrics();
        var adapter = new LangChainRiskAssessmentAdapter(
                service, new ObjectMapper(), metrics);
        AgentAssessment result;
        try (var logs = new com.omniguardy.backend.global.performance.PerformanceLogCapture()) {
            result = adapter.assess(new AgentContext("event", EventTriggerType.AUDIO,
                    new AgentContext.Audio(AudioEventType.KNOCK, 0.99),
                    new AgentContext.Vision("A18", 0.46, Map.of("A18", 0.46), 0)));
            var call = logs.stage("AGENT_LLM_CALL");
            assertEquals(120, call.path("inputTokens").asInt());
            assertEquals(30, call.path("outputTokens").asInt());
            assertEquals(150, call.path("totalTokens").asInt());
            assertTrue(logs.events().stream().anyMatch(log -> "AGENT_LLM_WAIT".equals(log.path("stage").asText())));
        }
        assertEquals(RiskLevel.HIGH, result.riskLevel());
        assertEquals(85, result.riskScore());
        assertEquals(1, metrics.snapshot().llmCallCount());
        verify(service).assess(argThat(json -> !json.contains("\"eventId\"")
                && !json.contains("details") && !json.contains("detectedFrame")));
    }

    @Test void rejectsInvalidRiskScore() {
        RiskAssessmentAiService service = mock(RiskAssessmentAiService.class);
        AgentStructuredResponse response = new AgentStructuredResponse();
        response.riskLevel = "HIGH";
        response.riskScore = 101;
        response.reason = "洹쇨굅";
        when(service.assess(anyString())).thenReturn(Result.<AgentStructuredResponse>builder().content(response).build());
        AgentPerformanceMetrics metrics = new AgentPerformanceMetrics();
        var adapter = new LangChainRiskAssessmentAdapter(
                service, new ObjectMapper(), metrics);
        assertThrows(BusinessException.class, () -> adapter.assess(new AgentContext("event", EventTriggerType.AUDIO,
                new AgentContext.Audio(AudioEventType.KNOCK, 1),
                new AgentContext.Vision("A18", 1, Map.of(), 0))));
        assertEquals(1, metrics.snapshot().llmCallCount());
    }
}

