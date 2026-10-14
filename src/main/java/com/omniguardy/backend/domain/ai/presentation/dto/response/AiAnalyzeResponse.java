package com.omniguardy.backend.domain.ai.presentation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiAnalyzeResponse {

    private VisionAnalyzeResponse vision;
    private AudioAnalyzeResponse audio;
}
