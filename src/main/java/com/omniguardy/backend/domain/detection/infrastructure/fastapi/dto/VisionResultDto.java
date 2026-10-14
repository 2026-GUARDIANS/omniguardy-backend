package com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class VisionResultDto {

    private String video;

    private String prediction;

    private double confidence;

    private Map<String, Double> classProbabilities;

    private int personCount;

    private double videoDurationSeconds;

    private String analyzedAt;
}
