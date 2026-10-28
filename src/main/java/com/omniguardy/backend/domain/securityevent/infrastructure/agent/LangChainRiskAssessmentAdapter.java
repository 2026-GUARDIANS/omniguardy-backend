package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import com.omniguardy.backend.domain.securityevent.application.metrics.AgentPerformanceMetrics;
import com.omniguardy.backend.domain.securityevent.application.port.out.RiskAssessmentPort;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentContext;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class LangChainRiskAssessmentAdapter implements RiskAssessmentPort {
    private final RiskAssessmentAiService aiService;
    private final ObjectMapper objectMapper;
    private final AgentPerformanceMetrics performanceMetrics;

    @Override
    public AgentAssessment assess(AgentContext context) {
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (JacksonException exception) {
            throw new BusinessException(SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID, exception);
        }

        long startedNanos = System.nanoTime();
        boolean success = false;
        Throwable failure = null;
        try {
            AgentStructuredResponse response = aiService.assess(contextJson);
            if (response == null) throw new BusinessException(SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID);
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
            performanceMetrics.recordLlmCall(context.triggerType(),
                    System.nanoTime() - startedNanos, success, failure);
        }
    }
}

