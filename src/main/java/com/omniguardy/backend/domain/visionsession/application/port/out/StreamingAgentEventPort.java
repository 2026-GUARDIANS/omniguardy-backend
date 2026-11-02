package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentJob;

public interface StreamingAgentEventPort {
    record Preparation(String eventId, Action action) {}
    enum Action { ASSESS, NO_EVENTS, NO_RISK, ALREADY_COMPLETED, STALE }
    enum EventState { ASSESSED, FAILED, OTHER }
    Preparation prepare(StreamingAgentJob job, VisionAnalysis analysis);
    void analyze(StreamingAgentJob job);
    EventState state(String eventId);
}
