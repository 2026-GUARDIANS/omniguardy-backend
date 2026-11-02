package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.metrics.AgentPerformanceMetrics;
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
    private static final String EXHIBITION_RULES = "exhibition_rules";
    private static final String TEMPORAL_TRIAL6 = "temporal_trial6";
    private final SecurityEventRepository securityEventRepository;
    private final RiskAssessmentPort riskAssessmentPort;
    private final ObjectMapper objectMapper;
    private final RiskResponseService riskResponseService;
    private final AgentPerformanceMetrics performanceMetrics;
    private com.omniguardy.backend.domain.securityevent.application.port.out.AgentExecutionGuard executionGuard;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExecutionGuard(com.omniguardy.backend.domain.securityevent.application.port.out.AgentExecutionGuard guard) {
        this.executionGuard = guard;
    }

    public AgentAssessment analyze(String eventId) {
        return analyzeReserved(eventId, null);
    }

    /** Same Agent contract/rules; fallback supplies its durable event reservation token. */
    public AgentAssessment analyzeReserved(String eventId, String reservationToken) {
        long startedNanos = System.nanoTime();
        EventTriggerType eventType = null;
        boolean success = false;
        Throwable failure = null;
        boolean assessmentRecorded = false;
        com.omniguardy.backend.domain.securityevent.application.port.out.AgentExecutionGuard.Claim execution = null;
        try {
            SecurityEvent event = securityEventRepository.findByEventId(eventId)
                    .orElseThrow(() -> new BusinessException(SecurityEventErrorCode.SECURITY_EVENT_NOT_FOUND));
            eventType = event.getTriggerType();
            if (executionGuard != null) {
                execution = executionGuard.begin(eventId, reservationToken);
                if (execution.alreadyCompleted()) {
                    success = true;
                    return execution.completedAssessment();
                }
            }
            if (!event.isVisionAnalyzed()) {
                throw new BusinessException(SecurityEventErrorCode.VISION_ANALYSIS_REQUIRED);
            }
            AgentAssessment assessment;
            try {
                AgentContext context = toContext(event);
                RiskLevel requiredRiskLevel = requiredRiskLevel(context);
                AgentAssessment agentAssessment = riskAssessmentPort.assess(context);
                assessment = new AgentAssessment(
                        requiredRiskLevel, agentAssessment.riskScore(), agentAssessment.reason());
                event.updateAgentResult(assessment.riskLevel().name(), assessment.riskScore(), assessment.reason());
                securityEventRepository.save(event);
                assessmentRecorded = true;
            } catch (RuntimeException exception) {
                event.fail();
                securityEventRepository.save(event);
                throw exception;
            }

            riskResponseService.respond(event, assessment.riskLevel());
            if (execution != null) executionGuard.succeeded(execution);
            success = true;
            return assessment;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            if (execution != null && !execution.alreadyCompleted()) {
                try { executionGuard.failed(execution, reservationToken != null || assessmentRecorded); }
                catch (RuntimeException ignored) { /* A durable CALLING row stays fenced when DB is unavailable. */ }
            }
            throw exception;
        } finally {
            performanceMetrics.recordAgentExecution(
                    eventType, System.nanoTime() - startedNanos, success, failure);
        }
    }

    private AgentContext toContext(SecurityEvent event) {
        return new AgentContext(event.getEventId(), event.getTriggerType(),
                new AgentContext.Audio(event.getAudioEventType(), value(event.getAudioProbability())),
                new AgentContext.Vision(event.getVisionPrediction(), event.getVisionConfidence(),
                        event.getVisionAnalysisMode(),
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
            String analysisMode = context.vision().analysisMode();
            if (EXHIBITION_RULES.equalsIgnoreCase(analysisMode)) {
                boolean hasOcclusion = context.vision().visionEvents().stream()
                        .anyMatch(this::isCameraOcclusionSuspected);
                boolean hasDwell = context.vision().visionEvents().stream()
                        .anyMatch(this::isPersonDwellObserved);
                if (hasOcclusion) return RiskLevel.MEDIUM;
                if (hasDwell) return RiskLevel.LOW;
                throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_CONTEXT);
            }
            if (analysisMode == null || analysisMode.isBlank()
                    || TEMPORAL_TRIAL6.equalsIgnoreCase(analysisMode)) {
                if ("A18".equalsIgnoreCase(context.vision().prediction())) return RiskLevel.MEDIUM;
                if ("A21".equalsIgnoreCase(context.vision().prediction())) return RiskLevel.LOW;
            }
        }

        throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_CONTEXT);
    }

    private boolean isHighRiskKeypadEvent(AgentContext.VisionEvent event) {
        return event != null && ("SILENT_SIGNAL".equalsIgnoreCase(event.eventType())
                || "REAR_CLOSE_APPROACH_SUSPECTED".equalsIgnoreCase(event.eventType()));
    }

    private boolean isPersonDwellObserved(AgentContext.VisionEvent event) {
        return event != null && "PERSON_DWELL_OBSERVED".equalsIgnoreCase(event.eventType());
    }

    private boolean isCameraOcclusionSuspected(AgentContext.VisionEvent event) {
        return event != null && "CAMERA_OCCLUSION_SUSPECTED".equalsIgnoreCase(event.eventType());
    }
}

