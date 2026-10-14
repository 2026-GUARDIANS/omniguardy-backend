package com.omniguardy.backend.domain.securityevent.domain.model;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import java.util.Map;
import java.util.List;

public record AgentContext(String eventId, EventTriggerType triggerType, Audio audio, Vision vision) {
    public AgentContext {
        if (eventId == null || eventId.isBlank()) throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_CONTEXT);
        if (audio == null || vision == null) throw new BusinessException(SecurityEventErrorCode.INVALID_AGENT_CONTEXT);
    }

    public record Audio(AudioEventType eventType, double probability) {}
    public record Vision(String prediction, double confidence,
                         Map<String, Double> classProbabilities, int personCount, List<VisionEvent> visionEvents) {
        public Vision(String prediction, double confidence, Map<String, Double> classProbabilities, int personCount) {
            this(prediction, confidence, classProbabilities, personCount, List.of());
        }
        public Vision {
            classProbabilities = classProbabilities == null ? Map.of() : Map.copyOf(classProbabilities);
            visionEvents = visionEvents == null ? List.of() : List.copyOf(visionEvents);
        }
    }

    public record VisionEvent(String eventType, Double confidence, Integer detectedFrame,
                              Map<String, Object> details) {}
}

