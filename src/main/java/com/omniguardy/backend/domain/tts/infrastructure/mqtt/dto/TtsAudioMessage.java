package com.omniguardy.backend.domain.tts.infrastructure.mqtt.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TtsAudioMessage {

    private String type;
    private String audioUrl;
}

