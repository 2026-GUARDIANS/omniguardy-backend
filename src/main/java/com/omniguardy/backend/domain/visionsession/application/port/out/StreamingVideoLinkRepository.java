package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType;
import java.util.List;
import java.util.Optional;

public interface StreamingVideoLinkRepository {
    record Candidate(String sessionId, VisionTriggerType triggerType, String triggerId, String eventId, String storagePath) {}
    Optional<Candidate> findVerified(String sessionId);
    List<String> storedSessionsAfter(String sessionId, int limit);
}
