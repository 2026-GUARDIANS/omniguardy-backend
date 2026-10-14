package com.omniguardy.backend.domain.ai.infrastructure.fastapi.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FastApiVisionResponse {

    private String status;
    private Result result;

    @Getter
    @NoArgsConstructor
    public static class Result {

        private String module;

        // 분석 결과 이름 또는 영상 이름
        private String video;

        private String events;

        @JsonAlias({"risk_score", "riskScore"})
        private int riskScore;

        @JsonAlias({"risk_level", "riskLevel"})
        private String riskLevel;

        // 탐지 결과가 표시된 영상 경로
        @JsonAlias({"annotatedVideo", "annotated_video"})
        private String annotatedVideo;
    }
}
