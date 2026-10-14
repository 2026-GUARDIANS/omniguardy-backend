package com.omniguardy.backend.domain.ai.presentation.mapper;

import com.omniguardy.backend.domain.ai.application.model.AnalysisResult;
import com.omniguardy.backend.domain.ai.presentation.dto.response.AiAnalyzeResponse;
import com.omniguardy.backend.domain.ai.presentation.dto.response.AudioAnalyzeResponse;
import com.omniguardy.backend.domain.ai.presentation.dto.response.VisionAnalyzeResponse;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class AiPresentationMapper {
    public MediaFile toMediaFile(MultipartFile file) throws IOException {
        return new MediaFile(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }

    public AiAnalyzeResponse toResponse(AnalysisResult result) {
        AnalysisResult.VisionResult vision = result.vision();
        AnalysisResult.AudioResult audio = result.audio();
        return AiAnalyzeResponse.builder()
                .vision(VisionAnalyzeResponse.builder().module(vision.module()).video(vision.video())
                        .events(vision.events()).riskScore(vision.riskScore()).riskLevel(vision.riskLevel())
                        .annotatedVideo(vision.annotatedVideo()).build())
                .audio(AudioAnalyzeResponse.builder().eventType(audio.eventType()).probability(audio.probability()).build())
                .build();
    }
}
