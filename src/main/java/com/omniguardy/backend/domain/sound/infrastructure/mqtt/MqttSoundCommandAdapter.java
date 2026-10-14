package com.omniguardy.backend.domain.sound.infrastructure.mqtt;

import com.omniguardy.backend.domain.sound.application.port.out.SoundCommandPort;
import com.omniguardy.backend.domain.sound.domain.model.SoundType;
import com.omniguardy.backend.global.mqtt.MqttConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MqttSoundCommandAdapter implements SoundCommandPort {

    private static final String TOPIC = "door/sound";

    private final MqttConfig.MqttGateway mqttGateway;
    private final JsonMapper jsonMapper;

    @Override
    public void send(SoundType type) {
        try {
            String payload = jsonMapper.writeValueAsString(
                    Map.of("type", type.name())
            );

            // payload 먼저, topic 두 번째
            mqttGateway.sendToMqtt(payload, TOPIC);

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Sound MQTT payload 생성에 실패했습니다.",
                    e
            );
        }
    }
}
