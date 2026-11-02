package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.visionsession.domain.error.StreamingVisionException;
import com.omniguardy.backend.domain.visionsession.infrastructure.streaming.StreamingVisionHttpAdapter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static com.omniguardy.backend.domain.visionsession.StreamingVisionTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class StreamingVisionHttpAdapterTest {
    record Response(int status, String body, long delayMs) {}
    HttpServer server;
    ExecutorService executor;
    BlockingQueue<Response> responses;
    List<String> requests;
    StreamingVisionHttpAdapter adapter;

    @BeforeEach void setup() throws Exception {
        responses = new LinkedBlockingQueue<>(); requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            var response = responses.poll();
            if (response == null) response = new Response(404, "{}", 0);
            try { Thread.sleep(response.delayMs()); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            try {
                byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        adapter = new StreamingVisionHttpAdapter(baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(1));
    }
    @AfterEach void close() { server.stop(0); executor.shutdownNow(); }
    String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    void respond(int code, String json) { responses.add(new Response(code, json, 0)); }

    @Test void queriesDedicatedStatusAndResultEndpointsAndParsesContract() {
        respond(200, statusJson("session", "trigger", "KEYPAD", null));
        respond(200, resultJson("session", "trigger", "KEYPAD", null));
        assertTrue(adapter.status("session").inputComplete());
        assertEquals(0, adapter.result("session").orElseThrow().trackedPersonCount());
        assertEquals(List.of("GET /vision/status/session", "GET /vision/result/session"), requests);
    }
    @Test void result202IsPendingAndDoesNotParseDetailAsFinalResult() {
        respond(202, "{\"detail\":\"analysis is still draining\"}");
        assertTrue(adapter.result("session").isEmpty());
    }
    @Test void notFoundIsPermanentForBothEndpoints() {
        respond(404, "{\"detail\":\"unknown sessionId\"}");
        assertFailure("UPSTREAM_NOT_FOUND", false, () -> adapter.status("session"));
        respond(404, "{}"); assertFailure("UPSTREAM_NOT_FOUND", false, () -> adapter.result("session"));
    }
    @Test void serverErrorsAndThrottlingAreRetryableButOtherClientErrorsArePermanent() {
        for (int code : new int[]{408, 429, 500, 502, 503}) {
            respond(code, "{}"); assertFailure("UPSTREAM_HTTP_" + code, true, () -> adapter.status("session"));
        }
        for (int code : new int[]{400, 401, 403, 422}) {
            respond(code, "{}"); assertFailure("UPSTREAM_HTTP_" + code, false, () -> adapter.result("session"));
        }
    }
    @Test void malformedOrWrongMessageTypeIsPermanentProtocolFailure() {
        for (String body : new String[]{"{", "null", "{}", resultJson("session", "trigger", "KEYPAD", null).replace("FINAL_RESULT", "EARLY_EVENT")}) {
            respond(200, body); assertFailure("INVALID_RESPONSE", false, () -> adapter.result("session"));
        }
    }
    @Test void readTimeoutAndConnectionFailureAreRetryable() {
        var shortTimeout = new StreamingVisionHttpAdapter(baseUrl(), Duration.ofMillis(200), Duration.ofMillis(100));
        responses.add(new Response(200, statusJson("session", "trigger", "KEYPAD", null), 500));
        assertFailure("NETWORK_ERROR", true, () -> shortTimeout.status("session"));
        server.stop(0);
        assertFailure("NETWORK_ERROR", true, () -> shortTimeout.status("session"));
    }
    void assertFailure(String code, boolean retryable, Runnable action) {
        var failure = assertThrows(StreamingVisionException.class, action::run);
        assertEquals(code, failure.code()); assertEquals(retryable, failure.retryable());
    }
}
