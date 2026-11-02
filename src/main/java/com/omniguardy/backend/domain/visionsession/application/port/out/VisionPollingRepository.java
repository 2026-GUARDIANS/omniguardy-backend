package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import java.time.Duration;
import java.util.*;

public interface VisionPollingRepository {
    Optional<VisionPollingJob> find(String sessionId);
    VisionPollingJob enqueue(VisionBinding binding, Duration maxWait);
    List<String> findDue(int limit);
    Optional<VisionPollingJob> claim(String sessionId, Duration leaseDuration);
    void reschedule(VisionPollingJob claim, Duration delay, String reason, String error, String statusJson);
    /** Fenced, atomic final insert + job finalization + vision_sessions status change. */
    void finish(VisionPollingJob claim, StreamingVisionFinalResult result, VisionFinalOutcome outcome,
                String reason, String error, String statusJson);
}
