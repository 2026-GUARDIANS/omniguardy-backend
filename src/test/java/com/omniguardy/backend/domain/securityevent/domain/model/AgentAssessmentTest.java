package com.omniguardy.backend.domain.securityevent.domain.model;

import com.omniguardy.backend.global.error.exception.BusinessException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentAssessmentTest {
    @Test void acceptsBoundaryScores() {
        assertEquals(0, new AgentAssessment(RiskLevel.LOW, 0, "洹쇨굅").riskScore());
        assertEquals(100, new AgentAssessment(RiskLevel.CRITICAL, 100, "洹쇨굅").riskScore());
    }
    @Test void rejectsOutOfRangeScore() {
        assertThrows(BusinessException.class, () -> new AgentAssessment(RiskLevel.HIGH, 101, "洹쇨굅"));
    }
}

