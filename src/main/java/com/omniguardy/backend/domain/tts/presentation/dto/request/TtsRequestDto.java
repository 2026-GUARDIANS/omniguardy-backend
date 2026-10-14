package com.omniguardy.backend.domain.tts.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class TtsRequestDto {

    @NotBlank(message = "異쒕젰??臾멸뎄???꾩닔?낅땲??")
    @Size(max = 100, message = "TTS 臾멸뎄??100???댄븯濡??낅젰?댁＜?몄슂.")
    private String message;
}
