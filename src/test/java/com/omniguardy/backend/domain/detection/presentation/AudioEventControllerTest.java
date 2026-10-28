package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioEventInputPort;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import com.omniguardy.backend.global.security.auth.AudioDeviceAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AudioEventControllerTest {
    private final ReceiveAudioEventInputPort port = mock(ReceiveAudioEventInputPort.class);
    private MockMvc mvc;
    static final String BODY = """
            {"detectionId":"pi-123","status":"success","predicted_class":"knock",
             "probabilities":{"background":0.01,"knock":0.98,"handle":0.01},
             "window_start_seconds":1.5,"cooldown_suppressed":true}
            """;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AudioEventController(port))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new AudioDeviceAuthenticationFilter("test-key", new ErrorResponseWriter(new ObjectMapper())))
                .build();
        when(port.receive(anyString(), any())).thenReturn(new AudioDetectionResult(
                "event-id", AudioDetectionResult.Outcome.TRIGGERED, null));
    }

    @ParameterizedTest @ValueSource(strings = {"knock", "handle", "background"})
    void mapsFastApiFormatWithoutUsingCooldownSuppressed(String type) throws Exception {
        mvc.perform(post("/api/internal/ai/audio-result").header("X-Device-Api-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"knock\",", "\"" + type + "\",")))
                .andExpect(status().isOk()).andExpect(jsonPath("data.detectionId").value("pi-123"))
                .andExpect(jsonPath("data.outcome").value("TRIGGERED"));
        verify(port).receive(eq("pi-123"), eq(new AudioAnalysis(AudioEventType.fromPredictedClass(type),
                type.equals("knock") ? 0.98 : 0.01)));
    }

    @Test void requiresDeviceKey() throws Exception {
        mvc.perform(post("/api/internal/ai/audio-result").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(post("/api/internal/ai/audio-result").header("X-Device-Api-Key", "wrong")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(port);
    }

    @Test void rejectsInvalidInputAndMalformedJson() throws Exception {
        for (String invalid : new String[]{"{", BODY.replace("pi-123", " "),
                BODY.replace("\"knock\",", "\"unknown\","), BODY.replace("0.98", "1.1"),
                BODY.replace("0.98", "null"), BODY.replace("\"probabilities\":{", "\"other\":{" )}) {
            mvc.perform(post("/api/internal/ai/audio-result").header("X-Device-Api-Key", "test-key")
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(port);
    }
}
