package com.omniguardy.backend.domain.visionsession.application.port.out;
import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.model.VisionFallbackSource;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionFallbackJob;
public interface VisionFallbackEventPort {
    /** Read-only conflict check before any MP4/AI I/O. */
    boolean alreadyOwned(VisionFallbackSource source, String linkedEventId);
    void prepare(VisionFallbackJob claim, VisionAnalysis analysis, VisionFallbackSource source);
    void analyze(VisionFallbackJob claim);
    boolean assessed(String eventId);
}

