package com.omniguardy.backend.domain.detection.application.port.out;
import com.omniguardy.backend.domain.detection.application.model.AudioAnalysis;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
public interface AudioAnalysisPort { AudioAnalysis analyze(MediaFile file); }

