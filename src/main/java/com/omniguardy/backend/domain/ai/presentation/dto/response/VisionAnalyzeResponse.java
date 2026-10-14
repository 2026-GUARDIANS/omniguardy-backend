package com.omniguardy.backend.domain.ai.presentation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VisionAnalyzeResponse {

    private String module;
    private String video;
    private String events;
    private int riskScore;
    private String riskLevel;

    // 프론트에서 재생할 탐지 결과 영상 URL
    private String annotatedVideo;
}
