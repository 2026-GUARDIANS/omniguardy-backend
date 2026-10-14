package com.omniguardy.backend.domain.detection.infrastructure.mqtt;

import com.omniguardy.backend.domain.detection.application.model.CameraStartCommand;
import com.omniguardy.backend.domain.detection.application.port.out.CameraCommandPort;
import com.omniguardy.backend.global.mqtt.MqttConfig.MqttGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MqttCameraCommandAdapter implements CameraCommandPort {

    private final MqttGateway mqttGateway;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.topic.camera}")
    private String cameraTopic;

    @Override
    public void startCamera(CameraStartCommand command) {
        CameraStartMessage message = new CameraStartMessage(
                "CAMERA_START",
                command.duration(),
                command.triggerId(),
                command.triggerType().name(),
                command.securityEventId(),
                command.triggeredAt().toString()
        );
        String payload = objectMapper.writeValueAsString(message);

        mqttGateway.sendToMqtt(payload, cameraTopic);
    }

    private record CameraStartMessage(
            String type,
            int duration,
            String triggerId,
            String triggerType,
            String securityEventId,
            String triggeredAt
    ) {
    }
}

