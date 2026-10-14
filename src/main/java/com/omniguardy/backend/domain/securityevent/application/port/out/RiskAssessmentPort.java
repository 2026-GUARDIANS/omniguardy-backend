package com.omniguardy.backend.domain.securityevent.application.port.out;

import com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentContext;

public interface RiskAssessmentPort {
    AgentAssessment assess(AgentContext context);
}

