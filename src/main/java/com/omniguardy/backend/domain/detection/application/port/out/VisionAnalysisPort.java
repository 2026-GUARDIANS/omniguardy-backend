package com.omniguardy.backend.domain.detection.application.port.out;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.model.VideoTriggerContext;
import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;

public interface VisionAnalysisPort {

    VisionAnalysis analyze(
            MediaFile file,
            VideoTriggerContext triggerContext
    );
}
