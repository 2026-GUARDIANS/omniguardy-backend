package com.omniguardy.backend.global.config;

import com.omniguardy.backend.global.mqtt.MqttConfig;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OMNIGUARDY_TEST_MQTT_URL", matches = ".+")
class MqttAuthenticationIntegrationTest {
    @Test void springConfiguredCredentialsPublishToAuthenticatedPiSubscriber() throws Exception {
        String uri = System.getenv("OMNIGUARDY_TEST_MQTT_URL");
        var config = new MqttConfig();
        ReflectionTestUtils.setField(config, "brokerUrl", uri);
        ReflectionTestUtils.setField(config, "username", "backend");
        ReflectionTestUtils.setField(config, "password", "validation-only");
        var factory = config.mqttClientFactory();
        var piOptions = new MqttConnectOptions(); piOptions.setUserName("raspberrypi");
        piOptions.setPassword("validation-only".toCharArray());
        var received = new CompletableFuture<String>();
        try (var pi = new MqttClient(uri, "pi-test-" + UUID.randomUUID(), new MemoryPersistence());
             var backend = new MqttClient(uri, "backend-test-" + UUID.randomUUID(), new MemoryPersistence())) {
            pi.connect(piOptions);
            pi.subscribe("door/sound", (topic, message) -> received.complete(new String(message.getPayload(), StandardCharsets.UTF_8)));
            backend.connect(factory.getConnectionOptions());
            backend.publish("door/sound", new MqttMessage("WARNING".getBytes(StandardCharsets.UTF_8)));
            assertEquals("WARNING", received.get(5, TimeUnit.SECONDS));
            backend.disconnect(); pi.disconnect();
        }
    }
}
