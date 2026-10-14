package com.omniguardy.backend.domain.detection.presentation.dto.response;

import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AudioUploadResponseDto {

    private String eventId;
    private String filename;
    private long size;
    private AudioEventType eventType;
    private double probability;
}
