package com.omniguardy.backend.domain.ai.application.usecase;

import com.omniguardy.backend.domain.ai.application.model.AnalysisResult;
import com.omniguardy.backend.domain.ai.application.port.out.MediaAnalysisPort;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyzeMediaUseCase {
    private final MediaAnalysisPort mediaAnalysisPort;

    public AnalysisResult analyze(MediaFile file) {
        return mediaAnalysisPort.analyze(file);
    }
}
