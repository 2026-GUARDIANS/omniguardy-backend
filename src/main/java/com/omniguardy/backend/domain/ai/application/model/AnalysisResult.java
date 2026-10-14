package com.omniguardy.backend.domain.ai.application.model;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;

public record AnalysisResult(VisionResult vision, AudioResult audio) {
    public record VisionResult(String module, String video, String events, int riskScore,
                               String riskLevel, String annotatedVideo) {}
    public record AudioResult(AudioEventType eventType, double probability) {}
}
