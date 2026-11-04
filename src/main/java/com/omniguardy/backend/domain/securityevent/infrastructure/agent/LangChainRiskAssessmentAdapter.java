package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import com.omniguardy.backend.domain.securityevent.application.metrics.AgentPerformanceMetrics;
import com.omniguardy.backend.domain.securityevent.application.port.out.RiskAssessmentPort;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentContext;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.omniguardy.backend.global.performance.PerformanceLog;

public class LangChainRiskAssessmentAdapter implements RiskAssessmentPort {
    private final RiskAssessmentAiService aiService;
    private final ObjectMapper objectMapper;
    private final AgentPerformanceMetrics performanceMetrics;
    private final AgentLlmConcurrencyLimiter concurrencyLimiter;

    public LangChainRiskAssessmentAdapter(RiskAssessmentAiService aiService, ObjectMapper objectMapper,
                                          AgentPerformanceMetrics performanceMetrics,
                                          AgentLlmConcurrencyLimiter concurrencyLimiter) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.performanceMetrics = performanceMetrics;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    public LangChainRiskAssessmentAdapter(RiskAssessmentAiService aiService, ObjectMapper objectMapper,
                                          AgentPerformanceMetrics performanceMetrics) {
        this(aiService, objectMapper, performanceMetrics, new AgentLlmConcurrencyLimiter(Integer.MAX_VALUE));
    }

    @Override
    public AgentAssessment assess(AgentContext context) {
        try (var scope = PerformanceLog.scope(context.eventId(), null, null, context.triggerType().name(), null)) {
            return assessContext(context);
        }
    }

    private AgentAssessment assessContext(AgentContext context) {
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(AgentPromptContext.from(context));
        } catch (JacksonException exception) {
            throw new BusinessException(SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID, exception);
        }

        return concurrencyLimiter.call(() -> invoke(context, contextJson));
    }

    private AgentAssessment invoke(AgentContext context, String contextJson) {
        long startedNanos = System.nanoTime();
        var span = PerformanceLog.start("AGENT_LLM_CALL");
        boolean success = false;
        Throwable failure = null;
        try {
            var result = aiService.assess(contextJson);
            AgentStructuredResponse response = result == null ? null : result.content();
            if (response == null) throw new BusinessException(SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID);
            if (result.tokenUsage() != null) PerformanceLog.tokens(result.tokenUsage().inputTokenCount(),
                    result.tokenUsage().outputTokenCount(), result.tokenUsage().totalTokenCount());
            AgentAssessment assessment = new AgentAssessment(
                    RiskLevel.valueOf(response.riskLevel), response.riskScore, response.reason);
            success = true;
            return assessment;
        } catch (IllegalArgumentException exception) {
            BusinessException businessException = new BusinessException(
                    SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID, exception);
            failure = businessException;
            throw businessException;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            if (!success) span.failure(failure);
            span.close();
            performanceMetrics.recordLlmCall(context.triggerType(),
                    System.nanoTime() - startedNanos, success, failure);
        }
    }
}

