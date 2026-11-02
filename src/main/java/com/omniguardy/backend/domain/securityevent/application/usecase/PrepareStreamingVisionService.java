package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.securityevent.application.port.in.*;
import com.omniguardy.backend.domain.securityevent.domain.model.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.securityevent.domain.error.StreamingVisionPreparationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** Applies evidence only. Existing record/assessment/response rules are reused, never reimplemented. */
@Service
@RequiredArgsConstructor
public class PrepareStreamingVisionService implements PrepareStreamingVisionInputPort {
    private final SecurityEventRepository events;
    private final RecordKeypadVisionInputPort keypad;
    private final ObjectMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Prepared prepare(VisionAnalysis vision, String previouslyLinkedEventId) {
        if (vision.triggerType() == TriggerType.KEYPAD && !vision.hasVisionEvents()) return new Prepared(null, false, false);
        String eventJson = mapper.writeValueAsString(vision.visionEvents());
        String id = vision.triggerType() == TriggerType.AUDIO ? vision.securityEventId() : previouslyLinkedEventId;
        if (id == null && vision.triggerType() == TriggerType.KEYPAD) {
            // Identity generation and create-or-replay remain entirely in the existing keypad UseCase.
            id = keypad.record(new RecordKeypadVisionInputPort.Command(vision.triggerId(), vision.triggeredAt(), null,
                    null, null, vision.analysisMode(), null, eventJson,
                    vision.observations().trackedPersonCount(), null, vision.analyzedAt()));
        }
        var existing = events.findByEventId(id);
        if (existing.isPresent()) {
            var event = existing.get();
            var type = vision.triggerType() == TriggerType.AUDIO ? EventTriggerType.AUDIO : EventTriggerType.KEYPAD;
            if (!id.equals(event.getEventId()) || event.getTriggerType() != type
                    || !vision.triggerId().equals(event.getTriggerId()) || event.getTriggeredAt() == null)
                throw new StreamingVisionPreparationException("EVENT_BINDING_MISMATCH");
            if ("COMPLETED".equals(event.getStatus())) return new Prepared(id, false, true);
            if (previouslyLinkedEventId == null && vision.triggerType() == TriggerType.AUDIO
                    && !"CAMERA_REQUESTED".equals(event.getStatus()))
                throw new StreamingVisionPreparationException("EVENT_ALREADY_OWNED");
            if (previouslyLinkedEventId != null && !previouslyLinkedEventId.equals(id))
                throw new StreamingVisionPreparationException("EVENT_BINDING_MISMATCH");
            if ((previouslyLinkedEventId != null || vision.triggerType() == TriggerType.KEYPAD)
                    && (!java.util.Set.of("VISION_ANALYZED", "FAILED").contains(event.getStatus())
                    || !java.util.Objects.equals(event.getVisionEvents(), eventJson)
                    || !java.util.Objects.equals(event.getVisionAnalysisMode(), vision.analysisMode())
                    || event.getVisionPrediction() != null || event.getVisionConfidence() != null))
                throw new StreamingVisionPreparationException("EVENT_EVIDENCE_CHANGED");
            event.updateVisionResult(null, null, vision.analysisMode(), null, eventJson,
                    vision.observations().trackedPersonCount(), null, vision.analyzedAt());
            boolean assess = vision.triggerType() == TriggerType.KEYPAD ? vision.hasHighRiskKeypadEvent() : vision.requiresAudioRiskAssessment();
            if (!assess) event.completeWithoutRisk();
            events.save(event);
            return new Prepared(id, assess, false);
        }
        throw new StreamingVisionPreparationException(vision.triggerType() == TriggerType.AUDIO
                ? "AUDIO_EVENT_NOT_FOUND" : "KEYPAD_EVENT_NOT_FOUND");
    }
}
