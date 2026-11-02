package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import java.time.Duration;
import java.util.*;

public interface StreamingAgentRepository {
    List<String> discover(int limit);
    void enqueue(String sessionId);
    Optional<StreamingAgentSource> source(String sessionId);
    Optional<StreamingAgentJob> find(String sessionId);
    List<String> due(int limit);
    Optional<StreamingAgentJob> claim(String sessionId, Duration lease);
    /** Both methods join the event preparation transaction and fence stale workers. */
    boolean lockPreparation(StreamingAgentJob job);
    void prepared(StreamingAgentJob job, String eventId);
    boolean startCalling(StreamingAgentJob job, Duration lease);
    boolean isCalling(StreamingAgentJob job);
    void finish(StreamingAgentJob job, StreamingAgentJob.State state, String reason, String responseState);
    void retry(StreamingAgentJob job, Duration delay, int maxAttempts, String reason);
    List<StreamingAgentJob> unresolved(int limit);
    void reconcile(StreamingAgentJob job, boolean completed);
}
