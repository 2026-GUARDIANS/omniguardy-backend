package com.omniguardy.backend.domain.detection.infrastructure.mqtt;

import com.omniguardy.backend.domain.detection.application.model.CameraStartCommand;
import com.omniguardy.backend.domain.detection.application.port.out.CameraCommandPort;
import com.omniguardy.backend.global.mqtt.MqttConfig.MqttGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MqttCameraCommandAdapter implements CameraCommandPort {

    private final MqttGateway mqttGateway;

    @Value("${mqtt.topic.camera}")
    private String cameraTopic;

    @Override
    public void startCamera(CameraStartCommand command) {

        String payload = """
                {
                  "type": "CAMERA_START",
                  "duration": %d,
                  "triggerId": "%s",
                  "triggerType": "%s",
                  "securityEventId": null,
                  "triggeredAt": "%s"
                }
                """.formatted(
                command.duration(),
                command.triggerId(),
                command.triggerType().name(),
                command.triggeredAt()
        );

        mqttGateway.sendToMqtt(payload, cameraTopic);
    }
}

