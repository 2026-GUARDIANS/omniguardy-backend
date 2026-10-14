package com.omniguardy.backend.domain.detection.presentation.dto.response;

import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VideoUploadResponseDto {

    private String eventId;

    private String filename;

    private VisionAnalysis vision;
}

