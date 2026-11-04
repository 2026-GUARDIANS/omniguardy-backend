package com.omniguardy.backend.domain.sound.infrastructure.mqtt;

import com.omniguardy.backend.domain.sound.application.port.out.SoundCommandPort;
import com.omniguardy.backend.domain.sound.domain.model.SoundType;
import com.omniguardy.backend.global.mqtt.MqttConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import com.omniguardy.backend.global.performance.PerformanceLog;

@Component
@RequiredArgsConstructor
public class MqttSoundCommandAdapter implements SoundCommandPort {

    private static final String TOPIC = "door/sound";

    private final MqttConfig.MqttGateway mqttGateway;
    private final JsonMapper jsonMapper;

    @Override
    public void send(SoundType type) {
        PerformanceLog.run("MQTT_SOUND_COMMAND", () -> {
            try {
                publish(type);
                PerformanceLog.result(type.name() + "_CLIENT_HANDOFF_ACCEPTED");
                PerformanceLog.elapsed("EVENT_TO_SOUND_COMMAND", true, null, "MQTT_CLIENT_HANDOFF");
            } catch (RuntimeException | Error failure) {
                PerformanceLog.elapsed("EVENT_TO_SOUND_COMMAND", false, failure, "MQTT_CLIENT_HANDOFF");
                throw failure;
            }
        });
    }

    private void publish(SoundType type) {
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
