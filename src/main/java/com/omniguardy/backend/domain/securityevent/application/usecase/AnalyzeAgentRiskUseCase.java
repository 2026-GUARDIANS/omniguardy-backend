package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.application.port.out.RiskAssessmentPort;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentAssessment;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentContext;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.model.RiskLevel;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyzeAgentRiskUseCase {
    private final SecurityEventRepository securityEventRepository;
    private final RiskAssessmentPort riskAssessmentPort;
    private final ObjectMapper objectMapper;

    public AgentAssessment analyze(String eventId) {
        SecurityEvent event = securityEventRepository.findByEventId(eventId)
                .orElseThrow(() -> new BusinessException(SecurityEventErrorCode.SECURITY_EVENT_NOT_FOUND));
        if (!event.isVisionAnalyzed()) {
            throw new BusinessException(SecurityEventErrorCode.VISION_ANALYSIS_REQUIRED);
        }
        try {
            AgentContext context = toContext(event);
            RiskLevel requiredRiskLevel = requiredRiskLevel(context);
            AgentAssessment agentAssessment = riskAssessmentPort.assess(context);
            AgentAssessment assessment = new AgentAssessment(
                    requiredRiskLevel, agentAssessment.riskScore(), agentAssessment.reason());
            event.updateAgentResult(assessment.riskLevel().name(), assessment.riskScore(), assessment.reason());
            securityEventRepository.save(event);
            return assessment;
        } catch (RuntimeException exception) {
            event.fail();
            securityEventRepository.save(event);
            throw exception;
        }
    }

    private AgentContext toContext(SecurityEvent event) {
        return new AgentContext(event.getEventId(), event.getTriggerType(),
                new AgentContext.Audio(event.getAudioEventType(), value(event.getAudioProbability())),
                new AgentContext.Vision(event.getVisionPrediction(), value(event.getVisionConfidence()),
                        probabilities(event.getClassProbabilities()),
                        event.getPersonCount() == null ? 0 : event.getPersonCount(), visionEvents(event.getVisionEvents())));
    }

    private List<AgentContext.VisionEvent> visionEvents(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<AgentContext.VisionEvent>>() {});
        } catch (JacksonException exception) {
            throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_CONTEXT, exception);
        }
    }

    private Map<String, Double> probabilities(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (JacksonException exception) {
            throw new BusinessException(SecurityEventErrorCode.INVALID_CLASS_PROBABILITIES, exception);
        }
    }

    private double value(Double value) { return value == null ? 0.0 : value; }

    private RiskLevel requiredRiskLevel(AgentContext context) {
        if (context.triggerType() == EventTriggerType.KEYPAD
                && context.vision().visionEvents().stream().anyMatch(this::isHighRiskKeypadEvent)) {
            return RiskLevel.HIGH;
        }

        if (context.triggerType() == EventTriggerType.AUDIO) {
            if ("A18".equalsIgnoreCase(context.vision().prediction())) return RiskLevel.MEDIUM;
            if ("A21".equalsIgnoreCase(context.vision().prediction())) return RiskLevel.LOW;
        }

        throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_CONTEXT);
    }

    private boolean isHighRiskKeypadEvent(AgentContext.VisionEvent event) {
        return event != null && ("SILENT_SIGNAL".equalsIgnoreCase(event.eventType())
                || "REAR_CLOSE_APPROACH_SUSPECTED".equalsIgnoreCase(event.eventType()));
    }
}

