package com.omniguardy.backend.domain.ai.presentation.dto.response;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AudioAnalyzeResponse {

    private AudioEventType eventType;
    private double probability;
}
