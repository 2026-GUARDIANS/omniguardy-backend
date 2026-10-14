package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

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

    @Override
    public AgentAssessment assess(AgentContext context) {
        try {
            AgentStructuredResponse response = aiService.assess(objectMapper.writeValueAsString(context));
            if (response == null) throw new BusinessException(SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID);
            return new AgentAssessment(RiskLevel.valueOf(response.riskLevel), response.riskScore, response.reason);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new BusinessException(SecurityEventErrorCode.AGENT_AI_RESPONSE_INVALID, exception);
        }
    }
}

