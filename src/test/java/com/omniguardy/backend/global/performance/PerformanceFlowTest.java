package com.omniguardy.backend.global.performance;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.detection.application.service.AudioDetectionService;
import com.omniguardy.backend.domain.detection.application.usecase.ReceiveAudioEventUseCase;
import com.omniguardy.backend.domain.detection.infrastructure.mqtt.MqttCameraCommandAdapter;
import com.omniguardy.backend.domain.notification.infrastructure.fcm.FirebasePushAdapter;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.sound.domain.model.SoundType;
import com.omniguardy.backend.domain.sound.infrastructure.mqtt.MqttSoundCommandAdapter;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.global.mqtt.MqttConfig.MqttGateway;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerformanceFlowTest {
    PerformanceLogCapture capture;
    PerformanceLog.Scope scope;
    @BeforeEach void setup() { capture = new PerformanceLogCapture(); scope = PerformanceLog.request("flow-request"); }
    @AfterEach void close() { scope.close(); capture.close(); }

    @Test void piResultIngressCreatesEventAndPublishesCameraWithoutAnyAudioAiOrFileCall() throws Exception {
        var gateway = mock(MqttGateway.class); var camera = new MqttCameraCommandAdapter(gateway, new JsonMapper());
        ReflectionTestUtils.setField(camera, "cameraTopic", "door/camera");
        var storage = mock(MediaStoragePort.class); var events = mock(SecurityEventRepository.class);
        var receipts = mock(AudioDetectionReceiptPort.class);
        var service = new AudioDetectionService(storage, camera, events, mock(DemoUserResolver.class), receipts);
        var audio = new ReceiveAudioEventUseCase(service);
        var result = new AtomicReference<AudioDetectionResult>();
        new PerformanceHttpFilter().doFilter(new MockHttpServletRequest("POST", "/api/internal/ai/audio-result"),
                new MockHttpServletResponse(), (request, response) -> result.set(audio.receive("pi-detection", new AudioAnalysis(AudioEventType.KNOCK, .9))));
        var http = capture.stage("AUDIO_RESULT_HTTP"); var process = capture.stage("AUDIO_RESULT_PROCESSING");
        var cameraLog = capture.stage("MQTT_CAMERA_START");
        assertEquals(result.get().eventId(), http.path("eventId").asText());
        assertEquals(http.path("eventId"), process.path("eventId")); assertEquals(http.path("eventId"), cameraLog.path("eventId"));
        assertEquals(http.path("requestId"), cameraLog.path("requestId"));
        assertEquals("pi-detection", http.path("detectionId").asText()); assertEquals(200, http.path("httpStatus").asInt());
        assertEquals("CLIENT_HANDOFF_ACCEPTED", cameraLog.path("resultCode").asText());
        assertEquals("MQTT_CLIENT_HANDOFF", cameraLog.path("observationPoint").asText());
        assertFalse(cameraLog.path("recipientDeliveryConfirmed").asBoolean());
        verify(gateway).sendToMqtt(anyString(), eq("door/camera")); verify(receipts).claim("pi-detection"); verify(events).save(any());
        verifyNoInteractions(storage);
        assertTrue(capture.events().stream().noneMatch(e -> e.path("stage").asText().contains("AUDIO_AI")));
    }

    @Test void mqttFailureRemainsFailureAndSoundPayloadAndPublicationCountStayUnchanged() {
        var gateway = mock(MqttGateway.class); var sound = new MqttSoundCommandAdapter(gateway, new JsonMapper());
        PerformanceLog.correlate("event", "trigger", "session", "KEYPAD");
        PerformanceLog.observedAt(Instant.now().minusSeconds(2), "SERVER_KEYPAD_TRIGGER_RECORDED");
        sound.send(SoundType.WARNING);
        verify(gateway).sendToMqtt("{\"type\":\"WARNING\"}", "door/sound");
        assertEquals("SUCCESS", capture.stage("MQTT_SOUND_COMMAND").path("status").asText());
        assertFalse(capture.stage("EVENT_TO_SOUND_COMMAND").path("recipientDeliveryConfirmed").asBoolean());
        var failure = new IllegalStateException("mqtt password must never be logged");
        doThrow(failure).when(gateway).sendToMqtt(anyString(), anyString());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> sound.send(SoundType.SIREN)));
        var last = capture.events().getLast(); assertEquals("FAILURE", last.path("status").asText());
        assertFalse(last.toString().contains("password"));
    }

    @Test void fcmSdkReturnIsMeasuredWithoutLoggingDeviceTokenNotificationTextOrProviderMessageId() throws Exception {
        var firebase = mock(FirebaseMessaging.class);
        try (var provider = mockStatic(FirebaseMessaging.class)) {
            provider.when(FirebaseMessaging::getInstance).thenReturn(firebase);
            when(firebase.send(any(Message.class))).thenReturn("private-provider-message-id");
            PerformanceLog.correlate("event", "trigger", "session", "AUDIO");
            PerformanceLog.observedAt(Instant.now().minusSeconds(2), "SERVER_AUDIO_TRIGGER_RECORDED");
            new FirebasePushAdapter().send("private-device-token", "private-title", "private-body", "SECURITY_EVENT", "2", "event");
            verify(firebase).send(any(Message.class));
            var event = capture.stage("FCM_SEND_REQUEST");
            assertEquals("SUCCESS", event.path("status").asText()); assertEquals("FCM_PROVIDER_API_RETURN", event.path("observationPoint").asText());
            assertFalse(event.path("recipientDeliveryConfirmed").asBoolean());
            assertTrue(capture.stage("EVENT_TO_FCM_REQUEST").path("durationMs").asDouble() >= 2_000);
            for (var log : capture.events()) assertFalse(log.toString().contains("private"));
        }
    }

    @Test void fcmFailureAndTimeoutKeepOriginalBusinessExceptionContract() throws Exception {
        var firebase = mock(FirebaseMessaging.class);
        try (var provider = mockStatic(FirebaseMessaging.class)) {
            provider.when(FirebaseMessaging::getInstance).thenReturn(firebase);
            when(firebase.send(any(Message.class))).thenThrow(new IllegalStateException("private-token", new java.net.SocketTimeoutException("private")));
            PerformanceLog.correlate("event", "trigger", null, "AUDIO");
            var failure = assertThrows(BusinessException.class,
                    () -> new FirebasePushAdapter().send("private-token", "private-title", "private-body", "SECURITY_EVENT", "2", "event"));
            assertEquals("PUSH_DELIVERY_FAILED", failure.getErrorCode().getCode());
            assertEquals("TIMEOUT", capture.stage("FCM_SEND_REQUEST").path("status").asText());
            assertEquals("TIMEOUT", capture.stage("EVENT_TO_FCM_REQUEST").path("status").asText());
            for (var log : capture.events()) assertFalse(log.toString().contains("private"));
        }
    }
}
