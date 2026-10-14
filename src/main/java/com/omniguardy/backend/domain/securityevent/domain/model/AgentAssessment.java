package com.omniguardy.backend.domain.securityevent.domain.model;

import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;

public record AgentAssessment(RiskLevel riskLevel, int riskScore, String reason) {
    public AgentAssessment {
        if (riskLevel == null) throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_ASSESSMENT);
        if (riskScore < 0 || riskScore > 100) throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_ASSESSMENT);
        if (reason == null || reason.isBlank()) throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_ASSESSMENT);
    }
}

