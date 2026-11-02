package com.omniguardy.backend.domain.visionsession.infrastructure.agent;

import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;
import com.omniguardy.backend.domain.securityevent.application.port.in.PrepareStreamingVisionInputPort;
import com.omniguardy.backend.domain.securityevent.application.usecase.AnalyzeAgentRiskUseCase;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentJob;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingAgentInputException;
import com.omniguardy.backend.domain.securityevent.domain.error.StreamingVisionPreparationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class ExistingStreamingAgentAdapter implements StreamingAgentEventPort {
    private final StreamingAgentRepository jobs;
    private final PrepareStreamingVisionInputPort preparation;
    private final AnalyzeAgentRiskUseCase agent;
    private final SecurityEventRepository events;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparation prepare(StreamingAgentJob job, VisionAnalysis vision) {
        if (!jobs.lockPreparation(job)) return new Preparation(null, Action.STALE);
        if (jobs.source(job.sessionId()).isEmpty()) throw new StreamingAgentInputException("INELIGIBLE_FINAL_RESULT");
        PrepareStreamingVisionInputPort.Prepared prepared;
        try { prepared = preparation.prepare(vision, job.eventId()); }
        catch (StreamingVisionPreparationException exception) { throw new StreamingAgentInputException(exception.getMessage()); }
        jobs.prepared(job, prepared.eventId());
        Action action = prepared.alreadyCompleted() ? Action.ALREADY_COMPLETED : prepared.assessmentRequired() ? Action.ASSESS
                : prepared.eventId() == null ? Action.NO_EVENTS : Action.NO_RISK;
        return new Preparation(prepared.eventId(), action);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void analyze(StreamingAgentJob job) {
        if (jobs.isCalling(job)) agent.analyze(job.eventId());
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public EventState state(String eventId) {
        if (eventId == null) return EventState.OTHER;
        return events.findByEventId(eventId).map(event -> {
            if ("COMPLETED".equals(event.getStatus()) && event.getRiskLevel() != null && event.getRiskScore() != null
                    && event.getAgentReason() != null) return EventState.ASSESSED;
            return "FAILED".equals(event.getStatus()) ? EventState.FAILED : EventState.OTHER;
        }).orElse(EventState.OTHER);
    }
}
