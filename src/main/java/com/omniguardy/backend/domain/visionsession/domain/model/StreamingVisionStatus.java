package com.omniguardy.backend.domain.visionsession.domain.model;

public record StreamingVisionStatus(VisionBinding binding, VisionAnalysisState analysisState,
                                    VisionAnalysisCompleteness analysisCompleteness, boolean inputComplete,
                                    boolean finalResultAvailable, String error, String rawJson) {}
