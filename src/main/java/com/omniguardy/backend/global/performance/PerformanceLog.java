package com.omniguardy.backend.global.performance;

import com.omniguardy.backend.global.error.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Allowlisted observation only. Never log arguments, payloads, URLs, or exception messages. */
public final class PerformanceLog {
    private static final Logger LOG = LoggerFactory.getLogger("omniguardy.performance");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Instant> REQUEST_RECEIVED_AT = new ThreadLocal<>();
    private static final ThreadLocal<Span> ACTIVE = new ThreadLocal<>();
    private static volatile boolean enabled = true;
    private static volatile int pollSampleEvery = 5;
    private PerformanceLog() {}

    public static void configure(boolean logEnabled, int sampleEvery) {
        enabled = logEnabled; pollSampleEvery = Math.max(1, sampleEvery);
    }

    public record Context(String eventId, String triggerId, String sessionId, String triggerType,
                          String detectionId, String requestId, Integer attempt, Instant observedAt, String baseline) {}

    public static Scope scope(String event, String trigger, String session, String type, Integer attempt) {
        var previous = CONTEXT.get();
        if (previous != null && (event != null && previous.eventId != null && !event.equals(previous.eventId)
                || session != null && !session.equals(previous.sessionId)))
            CONTEXT.set(new Context(null, null, null, null, null, previous.requestId, null, null, null));
        correlate(event, trigger, session, type);
        if (attempt != null) {
            var c = current();
            CONTEXT.set(new Context(c.eventId, c.triggerId, c.sessionId, c.triggerType, c.detectionId,
                    c.requestId, attempt, c.observedAt, c.baseline));
        }
        return () -> restore(previous);
    }

    public static Scope request(String requestId) {
        var previous = CONTEXT.get();
        var previousReceipt = REQUEST_RECEIVED_AT.get();
        REQUEST_RECEIVED_AT.set(Instant.now());
        CONTEXT.set(new Context(null, null, null, null, null, safe(requestId), null, null, null));
        return () -> {
            restore(previous);
            if (previousReceipt == null) REQUEST_RECEIVED_AT.remove(); else REQUEST_RECEIVED_AT.set(previousReceipt);
        };
    }

    /** Server HTTP ingress only; never a Vision/Pi clock. Null outside an HTTP request scope. */
    public static Instant requestReceivedAt() { return REQUEST_RECEIVED_AT.get(); }

    public static void correlate(String event, String trigger, String session, String type) {
        var c = current();
        CONTEXT.set(new Context(choose(event, c.eventId), choose(trigger, c.triggerId), choose(session, c.sessionId),
                "AUDIO".equals(type) || "KEYPAD".equals(type) ? type : c.triggerType,
                c.detectionId, c.requestId, c.attempt, c.observedAt, c.baseline));
    }

    public static void detection(String detectionId) {
        var c = current();
        CONTEXT.set(new Context(c.eventId, c.triggerId, c.sessionId, c.triggerType, safe(detectionId), c.requestId,
                c.attempt, c.observedAt, c.baseline));
    }

    public static void observedAt(Instant instant, String baseline) {
        var c = current();
        CONTEXT.set(new Context(c.eventId, c.triggerId, c.sessionId, c.triggerType, c.detectionId, c.requestId,
                c.attempt, instant, safe(baseline)));
    }

    public static Span start(String stage) { return new Span(stage); }
    public static <T> T measure(String stage, Supplier<T> work) {
        try (var span = start(stage)) {
            try { return work.get(); }
            catch (RuntimeException | Error failure) { span.failure(failure); throw failure; }
        }
    }
    public static void run(String stage, Runnable work) { measure(stage, () -> { work.run(); return null; }); }
    public static void transaction(String stage, Runnable work) {
        try (var span = start(stage)) {
            span.transaction = true;
            try { work.run(); }
            catch (RuntimeException | Error failure) { span.failure(failure); throw failure; }
        }
    }
    public static void result(String code) { if (ACTIVE.get() != null) ACTIVE.get().resultCode = safe(code); }
    public static void httpStatus(int status) { if (ACTIVE.get() != null) ACTIVE.get().httpStatus = status; }
    public static void failure(Throwable failure) { if (ACTIVE.get() != null) ACTIVE.get().failure(failure); }
    public static void terminal() { if (ACTIVE.get() != null) ACTIVE.get().terminal = true; }
    public static void tokens(Integer input, Integer output, Integer total) {
        if (ACTIVE.get() != null) {
            ACTIVE.get().inputTokens = input;
            ACTIVE.get().outputTokens = output;
            ACTIVE.get().totalTokens = total;
        }
    }

    /** 이미 시작된 queue 대기처럼 호출 시점 이전 구간을 monotonic duration으로 기록한다. */
    public static void duration(String stage, long durationNanos) {
        Instant end = Instant.now();
        var f = fields(stage, null, end, durationNanos / 1_000_000.0, "SUCCESS", null);
        f.put("startedAt", null);
        f.put("timingBasis", "MONOTONIC_DURATION_ONLY");
        emit(f);
    }

    /** Cross-worker elapsed time uses a persisted UTC instant, never another thread/process nanoTime. */
    public static void elapsed(String stage, boolean success, Throwable failure, String observationPoint) {
        var c = current();
        if (c.eventId == null && c.triggerId == null) return;
        Instant end = Instant.now();
        Double millis = c.observedAt == null ? null : (double) Duration.between(c.observedAt, end).toMillis();
        var fields = fields(stage, c.observedAt, end, millis, success ? "SUCCESS" : status(failure), failure);
        fields.put("timingBasis", "PERSISTED_WALL_CLOCK");
        fields.put("baseline", c.baseline == null ? "UNAVAILABLE" : c.baseline);
        fields.put("clockAnomaly", millis != null && millis < 0);
        fields.put("observationPoint", observationPoint);
        fields.put("recipientDeliveryConfirmed", false);
        emit(fields);
    }

    private static Map<String, Object> fields(String stage, Instant start, Instant end, Double ms, String status, Throwable failure) {
        var c = current();
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("schemaVersion", 1); f.put("timestamp", end.toString()); f.put("source", "SPRING"); f.put("stage", stage);
        f.put("startedAt", start == null ? null : start.toString()); f.put("completedAt", end.toString());
        f.put("eventId", c.eventId); f.put("triggerId", c.triggerId); f.put("sessionId", c.sessionId); f.put("triggerType", c.triggerType);
        f.put("detectionId", c.detectionId); f.put("requestId", c.requestId); f.put("durationMs", ms); f.put("status", status);
        f.put("attempt", c.attempt); f.put("retryCount", c.attempt == null ? null : Math.max(0, c.attempt - 1));
        f.put("timingBasis", "MONOTONIC");
        if (failure != null) {
            f.put("errorType", failure.getClass().getSimpleName());
            if (failure instanceof BusinessException business) f.put("resultCode", business.getErrorCode().getCode());
        }
        return f;
    }

    private static String status(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException
                    || cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof org.eclipse.paho.client.mqttv3.MqttException mqtt
                        && (mqtt.getReasonCode() == org.eclipse.paho.client.mqttv3.MqttException.REASON_CODE_CLIENT_TIMEOUT
                            || mqtt.getReasonCode() == org.eclipse.paho.client.mqttv3.MqttException.REASON_CODE_WRITE_TIMEOUT)
                    || cause.getClass().getSimpleName().contains("Timeout")) return "TIMEOUT";
        }
        return "FAILURE";
    }

    private static void emit(Map<String, Object> fields) {
        if (!enabled || !LOG.isInfoEnabled()) return;
        try { LOG.info("{}", JSON.writeValueAsString(fields)); }
        catch (RuntimeException ignored) { /* Observability must never change a business outcome. */ }
    }
    private static String safe(String value) { return value != null && value.matches("[A-Za-z0-9._:-]{1,128}") ? value : null; }
    private static String choose(String value, String existing) { return value == null ? existing : safe(value); }
    private static Context current() {
        var c = CONTEXT.get();
        return c == null ? new Context(null, null, null, null, null, null, null, null, null) : c;
    }
    private static void restore(Context c) { if (c == null) CONTEXT.remove(); else CONTEXT.set(c); }

    public interface Scope extends AutoCloseable { @Override void close(); }

    public static final class Span implements AutoCloseable {
        private final String stage;
        private final Instant startedAt = Instant.now();
        private final long startedNanos = System.nanoTime();
        private final Span previous;
        private String resultCode;
        private Integer httpStatus;
        private Throwable failure;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
        private boolean terminal;
        private boolean transaction;
        private boolean closed;
        private Span(String stage) { this.stage = stage; previous = ACTIVE.get(); ACTIVE.set(this); }
        public void failure(Throwable value) { if (failure == null) failure = value; }
        public void response(int value) { httpStatus = value; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                var context = current();
                if (transaction && TransactionSynchronizationManager.isActualTransactionActive()
                        && TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) { complete(context, status); }
                    });
                } else complete(context, null);
            } finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
        }
        private void complete(Context context, Integer transactionStatus) {
            var original = CONTEXT.get();
            CONTEXT.set(context);
            try {
                var c = current();
                boolean failed = failure != null || httpStatus != null && httpStatus >= 400
                        || transactionStatus != null && transactionStatus != TransactionSynchronization.STATUS_COMMITTED;
                boolean polling = stage.equals("VISION_STATUS_HTTP") || stage.equals("VISION_RESULT_HTTP");
                if (polling && !failed && !terminal && c.attempt != null && c.attempt > 1 && c.attempt % pollSampleEvery != 0) return;
                var f = fields(stage, startedAt, Instant.now(), (System.nanoTime() - startedNanos) / 1_000_000.0,
                        failed ? (httpStatus != null && (httpStatus == 408 || httpStatus == 504) ? "TIMEOUT" : status(failure)) : "SUCCESS", failure);
                if (resultCode != null) f.put("resultCode", resultCode);
                if (httpStatus != null) f.put("httpStatus", httpStatus);
                if (transactionStatus != null) f.put("transactionStatus",
                        transactionStatus == TransactionSynchronization.STATUS_COMMITTED ? "COMMITTED" : "ROLLED_BACK");
                if (polling) f.put("sampleEvery", pollSampleEvery);
                if (inputTokens != null) f.put("inputTokens", inputTokens);
                if (outputTokens != null) f.put("outputTokens", outputTokens);
                if (totalTokens != null) f.put("totalTokens", totalTokens);
                if (stage.equals("MQTT_CAMERA_START") || stage.equals("MQTT_SOUND_COMMAND")) {
                    f.put("observationPoint", "MQTT_CLIENT_HANDOFF"); f.put("recipientDeliveryConfirmed", false);
                }
                if (stage.equals("FCM_SEND_REQUEST")) {
                    f.put("observationPoint", "FCM_PROVIDER_API_RETURN"); f.put("recipientDeliveryConfirmed", false);
                }
                emit(f);
            } finally { restore(original); }
        }
    }
}
