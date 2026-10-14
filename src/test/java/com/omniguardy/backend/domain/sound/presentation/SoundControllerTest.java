package com.omniguardy.backend.domain.sound.presentation;

import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.sound.application.service.PlaySoundService;
import com.omniguardy.backend.domain.sound.infrastructure.mqtt.MqttSoundCommandAdapter;
import com.omniguardy.backend.global.mqtt.MqttConfig.MqttGateway;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SoundControllerTest {

    @ParameterizedTest
    @ValueSource(strings = {"WARNING", "SIREN"})
    void preservesHttpAndMqttContracts(String type) throws Exception {
        MqttGateway gateway = mock(MqttGateway.class);
        var adapter = new MqttSoundCommandAdapter(gateway, JsonMapper.builder().build());
        var controller = new SoundController(new PlaySoundService(adapter));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/sounds/{type}", type))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(gateway).sendToMqtt("{\"type\":\"" + type + "\"}", "door/sound");
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void rejectsInvalidSoundType() throws Exception {
        PlaySoundUseCase useCase = mock(PlaySoundUseCase.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SoundController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/sounds/INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(GlobalErrorCode.INVALID_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value(GlobalErrorCode.INVALID_REQUEST.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(useCase);
    }
}
