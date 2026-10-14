package com.omniguardy.backend.domain.ai.application.port.out;

import com.omniguardy.backend.domain.ai.application.model.AnalysisResult;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;

public interface MediaAnalysisPort {
    AnalysisResult analyze(MediaFile file);
}
