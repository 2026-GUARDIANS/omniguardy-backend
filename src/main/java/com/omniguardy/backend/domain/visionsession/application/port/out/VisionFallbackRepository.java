package com.omniguardy.backend.domain.visionsession.application.port.out;
import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import java.util.*;
public interface VisionFallbackRepository {
    void discover(VisionFallbackPolicy policy);
    List<String> due(int limit);
    Optional<VisionFallbackJob> claim(String id, VisionFallbackPolicy policy);
    Optional<VisionFallbackJob> find(String id);
    VisionFallbackSource source(VisionFallbackJob claim);
    boolean owns(VisionFallbackJob claim);
    boolean beginVision(VisionFallbackJob claim, StreamingSessionVideo video, int maxAttempts);
    boolean analyzed(VisionFallbackJob claim, VisionAnalysis analysis);
    void waitForVideo(VisionFallbackJob claim, VisionFallbackPolicy policy);
    void retry(VisionFallbackJob claim, VisionFallbackPolicy policy, String reason, boolean permanent);
    void finish(VisionFallbackJob claim, VisionFallbackJob.State state, String reason);
    void prepared(VisionFallbackJob claim, String eventId, boolean assess, String reason);
    boolean startCalling(VisionFallbackJob claim, VisionFallbackPolicy policy);
    boolean isCalling(VisionFallbackJob claim);
    List<VisionFallbackJob> interrupted(int limit);
    void reconcile(VisionFallbackJob claim, boolean assessed);
}

