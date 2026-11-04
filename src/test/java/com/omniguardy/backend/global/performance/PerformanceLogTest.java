package com.omniguardy.backend.global.performance;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceLogTest {
    PerformanceLogCapture capture;
    PerformanceLog.Scope scope;
    @BeforeEach void setup() { PerformanceLog.configure(true, 5); capture = new PerformanceLogCapture(); scope = PerformanceLog.request("request-test"); }
    @AfterEach void close() { scope.close(); capture.close(); PerformanceLog.configure(true, 5); }

    @Test void emitsUtcJsonWithCorrelationAndMonotonicDurationWithoutSensitivePayloads() {
        PerformanceLog.correlate("event", "trigger", "session", "AUDIO"); PerformanceLog.detection("pi-detection");
        assertEquals(42, PerformanceLog.measure("TEST_STAGE", () -> 42));
        var event = capture.stage("TEST_STAGE");
        assertEquals("SPRING", event.path("source").asText()); assertEquals("SUCCESS", event.path("status").asText());
        assertEquals("event", event.path("eventId").asText()); assertEquals("trigger", event.path("triggerId").asText());
        assertEquals("session", event.path("sessionId").asText()); assertEquals("pi-detection", event.path("detectionId").asText());
        assertTrue(event.path("durationMs").asDouble() >= 0); assertEquals("MONOTONIC", event.path("timingBasis").asText());
        assertFalse(Instant.parse(event.path("completedAt").asText()).isBefore(Instant.parse(event.path("startedAt").asText())));
        assertEquals(event.path("completedAt").asText(), event.path("timestamp").asText());
    }

    @Test void preservesThrownObjectAndClassifiesNestedTimeoutWithoutLoggingMessage() {
        var failure = new IllegalStateException("JWT-secret API-key private@example.com", new SocketTimeoutException("secret-token"));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> PerformanceLog.run("FAILURE", () -> { throw failure; })));
        var event = capture.stage("FAILURE"); assertEquals("TIMEOUT", event.path("status").asText());
        assertFalse(event.toString().contains("secret")); assertFalse(event.toString().contains("private@"));
        var mqtt = new org.eclipse.paho.client.mqttv3.MqttException(org.eclipse.paho.client.mqttv3.MqttException.REASON_CODE_CLIENT_TIMEOUT);
        assertThrows(IllegalStateException.class, () -> PerformanceLog.run("MQTT_TIMEOUT", () -> { throw new IllegalStateException(mqtt); }));
        assertEquals("TIMEOUT", capture.stage("MQTT_TIMEOUT").path("status").asText());
    }

    @Test void samplesPendingPollsButAlwaysLogsFirstTerminalAndFailedAttempts() {
        try (var context = PerformanceLog.scope(null, "trigger", "session", "KEYPAD", 2)) {
            PerformanceLog.run("VISION_STATUS_HTTP", () -> PerformanceLog.httpStatus(200));
            assertTrue(capture.events().isEmpty());
            PerformanceLog.run("VISION_RESULT_HTTP", () -> { PerformanceLog.httpStatus(202); PerformanceLog.terminal(); });
            assertEquals(1, capture.events().size());
            assertThrows(IllegalStateException.class, () -> PerformanceLog.run("VISION_STATUS_HTTP", () -> { throw new IllegalStateException(); }));
            assertEquals(2, capture.events().size());
        }
        for (int attempt : new int[]{1, 5}) {
            try (var context = PerformanceLog.scope(null, null, "session", null, attempt)) {
                PerformanceLog.run("VISION_STATUS_HTTP", () -> {});
            }
        }
        assertEquals(4, capture.events().size());
        assertEquals(1, capture.events().getFirst().path("retryCount").asInt());
    }

    @Test void elapsedUsesPersistedWallClockAndExplicitlyReportsMissingBaseline() {
        PerformanceLog.correlate("event", "trigger", null, "AUDIO");
        PerformanceLog.observedAt(Instant.now().minusSeconds(10), "SERVER_AUDIO_TRIGGER_RECORDED");
        PerformanceLog.elapsed("EVENT_TO_SOUND_COMMAND", true, null, "MQTT_CLIENT_HANDOFF");
        var event = capture.stage("EVENT_TO_SOUND_COMMAND");
        assertEquals("PERSISTED_WALL_CLOCK", event.path("timingBasis").asText()); assertTrue(event.path("durationMs").asDouble() >= 10_000);
        assertFalse(event.path("recipientDeliveryConfirmed").asBoolean());
        PerformanceLog.observedAt(null, "UNAVAILABLE");
        PerformanceLog.elapsed("MISSING", true, null, "MQTT_CLIENT_HANDOFF");
        assertTrue(capture.stage("MISSING").path("durationMs").isNull());
    }

    @Test void transactionLogsOnlyAfterCommitOrRollbackAndRetainsOriginalContext() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        var jdbc = new JdbcTemplate(ds); var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.executeWithoutResult(status -> {
            try (var context = PerformanceLog.scope("committed-event", "trigger", "session", "AUDIO", 1)) {
                PerformanceLog.transaction("COMMIT", () -> jdbc.execute("CREATE TABLE sample (id INT)"));
            }
            assertTrue(capture.events().isEmpty());
        });
        assertEquals("COMMITTED", capture.stage("COMMIT").path("transactionStatus").asText());
        assertEquals("committed-event", capture.stage("COMMIT").path("eventId").asText());
        tx.executeWithoutResult(status -> { PerformanceLog.transaction("ROLLBACK", () -> {}); status.setRollbackOnly(); });
        assertEquals("ROLLED_BACK", capture.stage("ROLLBACK").path("transactionStatus").asText());
        assertEquals("FAILURE", capture.stage("ROLLBACK").path("status").asText());
    }

    @Test void ingressFilterRecordsRejectionAndPropagatesExceptionWithoutReadingCredentials() throws Exception {
        var filter = new PerformanceHttpFilter();
        var request = new MockHttpServletRequest("POST", "/api/internal/ai/audio-result");
        request.addHeader("Authorization", "Bearer private-token"); request.addHeader("X-Device-Api-Key", "private-key");
        request.setContent("private-payload".getBytes());
        filter.doFilter(request, new MockHttpServletResponse(), (req, response) -> ((MockHttpServletResponse) response).setStatus(401));
        var event = capture.stage("AUDIO_RESULT_HTTP"); assertEquals(401, event.path("httpStatus").asInt());
        assertEquals("FAILURE", event.path("status").asText()); assertFalse(event.toString().contains("private"));
        var failure = new jakarta.servlet.ServletException(new SocketTimeoutException("private"));
        assertSame(failure, assertThrows(jakarta.servlet.ServletException.class,
                () -> filter.doFilter(new MockHttpServletRequest("POST", "/api/edge/vision/sessions"),
                        new MockHttpServletResponse(), (req, response) -> { throw failure; })));
        assertEquals("TIMEOUT", capture.stage("VISION_SESSION_HTTP").path("status").asText());
        assertFalse(capture.stage("VISION_SESSION_HTTP").has("httpStatus"));
    }

    @Test void disabledLoggingKeepsWorkAndContextNeverLeaksBetweenRequestsOrDifferentEvents() {
        PerformanceLog.configure(false, 5);
        assertEquals("result", PerformanceLog.measure("DISABLED", () -> "result")); assertTrue(capture.events().isEmpty());
        PerformanceLog.configure(true, 5);
        PerformanceLog.correlate("old-event", "old-trigger", "old-session", "KEYPAD");
        try (var context = PerformanceLog.scope("new-event", null, null, "AUDIO", null)) {
            PerformanceLog.run("NEW_EVENT", () -> {});
        }
        assertTrue(capture.stage("NEW_EVENT").path("triggerId").isNull());
        try (var request = PerformanceLog.request("another-request")) { PerformanceLog.run("NEW_REQUEST", () -> {}); }
        assertTrue(capture.stage("NEW_REQUEST").path("eventId").isNull());
    }
}
