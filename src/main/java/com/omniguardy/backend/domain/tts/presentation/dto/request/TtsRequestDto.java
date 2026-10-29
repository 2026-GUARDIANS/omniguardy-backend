package com.omniguardy.backend.domain.tts.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class TtsRequestDto {

    @Schema(description = "도어락 스피커로 재생할 안내 문구(최대 100자)",
            example = "문 앞에서 물러나 주세요. 보호자에게 알림이 전송되었습니다.")
    @NotBlank(message = "異쒕젰??臾멸뎄???꾩닔?낅땲??")
    @Size(max = 100, message = "TTS 臾멸뎄??100???댄븯濡??낅젰?댁＜?몄슂.")
    private String message;
}
