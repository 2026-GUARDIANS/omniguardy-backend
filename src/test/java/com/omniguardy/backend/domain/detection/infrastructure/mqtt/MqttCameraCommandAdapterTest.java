package com.omniguardy.backend.domain.detection.infrastructure.mqtt;

import com.omniguardy.backend.domain.detection.application.model.CameraStartCommand;
import com.omniguardy.backend.domain.detection.application.model.TriggerType;
import com.omniguardy.backend.global.mqtt.MqttConfig.MqttGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqttCameraCommandAdapterTest {

    private final MqttGateway mqttGateway = mock(MqttGateway.class);
    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final MqttCameraCommandAdapter adapter =
            new MqttCameraCommandAdapter(mqttGateway, objectMapper);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "cameraTopic", "door/camera");
    }

    @Test
    void audioCommandIncludesSecurityEventId() {
        adapter.startCamera(command(TriggerType.AUDIO, "security-event"));

        JsonNode payload = capturePayload();
        assertEquals("CAMERA_START", payload.get("type").asText());
        assertEquals("trigger", payload.get("triggerId").asText());
        assertEquals("AUDIO", payload.get("triggerType").asText());
        assertEquals("security-event", payload.get("securityEventId").asText());
    }

    @Test
    void keypadCommandKeepsSecurityEventIdAsJsonNull() {
        adapter.startCamera(command(TriggerType.KEYPAD, null));

        JsonNode payload = capturePayload();
        assertEquals("KEYPAD", payload.get("triggerType").asText());
        assertTrue(payload.get("securityEventId").isNull());
    }

    private CameraStartCommand command(TriggerType triggerType, String securityEventId) {
        return new CameraStartCommand(
                "trigger",
                triggerType,
                securityEventId,
                OffsetDateTime.parse("2026-10-12T12:34:56+09:00"),
                10
        );
    }

    private JsonNode capturePayload() {
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttGateway).sendToMqtt(payloadCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("door/camera"));
        return objectMapper.readTree(payloadCaptor.getValue());
    }
}
