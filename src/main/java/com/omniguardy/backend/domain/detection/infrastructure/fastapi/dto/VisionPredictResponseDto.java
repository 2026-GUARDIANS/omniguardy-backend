package com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VisionPredictResponseDto {

    private String status;
    private VisionResultDto result;

    // 실패 응답일 때 사용
    private String message;
    private String code;
}
