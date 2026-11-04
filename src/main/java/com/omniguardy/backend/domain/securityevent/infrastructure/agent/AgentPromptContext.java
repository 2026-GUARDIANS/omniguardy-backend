package com.omniguardy.backend.domain.securityevent.infrastructure.agent;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.securityevent.domain.model.AgentContext;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;

import java.util.List;
import java.util.Map;

/** LLM 판단에 필요한 증거만 담아 가변 Vision 원문이 프롬프트에 중복 유입되지 않게 한다. */
record AgentPromptContext(EventTriggerType triggerType, Audio audio, Vision vision) {
    static AgentPromptContext from(AgentContext context) {
        var vision = context.vision();
        return new AgentPromptContext(context.triggerType(),
                new Audio(context.audio().eventType(), context.audio().probability()),
                new Vision(vision.prediction(), vision.confidence(), vision.analysisMode(),
                        vision.classProbabilities(), vision.personCount(),
                        vision.visionEvents().stream()
                                .map(event -> new VisionEvent(event.eventType(), event.confidence()))
                                .toList()));
    }

    record Audio(AudioEventType eventType, double probability) {}

    record Vision(String prediction, Double confidence, String analysisMode,
                  Map<String, Double> classProbabilities, int personCount,
                  List<VisionEvent> visionEvents) {}

    record VisionEvent(String eventType, Double confidence) {}
}
