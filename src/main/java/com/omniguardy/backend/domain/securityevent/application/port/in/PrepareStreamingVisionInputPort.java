package com.omniguardy.backend.domain.securityevent.application.port.in;

import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;

public interface PrepareStreamingVisionInputPort {
    record Prepared(String eventId, boolean assessmentRequired, boolean alreadyCompleted) {}
    Prepared prepare(VisionAnalysis analysis, String previouslyLinkedEventId);
}
