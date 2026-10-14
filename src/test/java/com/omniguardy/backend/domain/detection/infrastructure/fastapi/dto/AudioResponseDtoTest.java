package com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto;

import com.omniguardy.backend.domain.ai.infrastructure.fastapi.dto.FastApiAudioResponse;
import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioResponseDtoTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void mapsChangedAudioResponseBody() {
        String responseBody = """
                {
                  "status": "KNOCK_EVENT",
                  "predicted_class": "knock",
                  "probabilities": {
                    "background": 0.002,
                    "knock": 0.995,
                    "handle": 0.003
                  },
                  "window_start_seconds": 0.8,
                  "cooldown_suppressed": false
                }
                """;

        AudioPredictResponseDto response = mapper.readValue(responseBody, AudioPredictResponseDto.class);

        assertEquals("KNOCK_EVENT", response.getStatus());
        assertEquals("knock", response.getPredictedClass());
        assertEquals(0.002, response.getProbabilities().getBackground());
        assertEquals(0.995, response.getProbabilities().getKnock());
        assertEquals(0.003, response.getProbabilities().getHandle());
        assertEquals(0.8, response.getWindowStartSeconds());
        assertFalse(response.isCooldownSuppressed());
        assertEquals(0.995, response.getPredictedProbability());
    }

    @Test
    void mapsChangedAudioResponseBodyForMediaAnalysis() {
        String responseBody = """
                {
                  "status": "HANDLE_EVENT",
                  "predicted_class": "handle",
                  "probabilities": {
                    "background": 0.015,
                    "knock": 0.021,
                    "handle": 0.964
                  },
                  "window_start_seconds": 1.2,
                  "cooldown_suppressed": false
                }
                """;

        FastApiAudioResponse response = mapper.readValue(responseBody, FastApiAudioResponse.class);

        assertEquals("HANDLE_EVENT", response.getStatus());
        assertEquals("handle", response.getPredictedClass());
        assertEquals(0.015, response.getProbabilities().getBackground());
        assertEquals(0.021, response.getProbabilities().getKnock());
        assertEquals(0.964, response.getProbabilities().getHandle());
        assertEquals(1.2, response.getWindowStartSeconds());
        assertFalse(response.isCooldownSuppressed());
        assertEquals(0.964, response.getPredictedProbability());
    }

    @Test
    void mapsEveryPredictedClassToAudioEventType() {
        assertEquals(AudioEventType.BACKGROUND, AudioEventType.fromPredictedClass("background"));
        assertEquals(AudioEventType.KNOCK, AudioEventType.fromPredictedClass("knock"));
        assertEquals(AudioEventType.HANDLE, AudioEventType.fromPredictedClass("handle"));
    }

    @Test
    void rejectsUnknownPredictedClass() {
        assertThrows(IllegalArgumentException.class,
                () -> AudioEventType.fromPredictedClass("unknown"));
    }
}
