package com.omniguardy.backend.domain.visionsession.infrastructure.streaming;

import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVisionQueryPort;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingVisionException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import com.omniguardy.backend.global.performance.PerformanceLog;

/** A dedicated client. It never calls /vision/complete or the legacy MP4 API. */
public class StreamingVisionHttpAdapter implements StreamingVisionQueryPort {
    private final RestClient client;
    private final StreamingVisionJsonMapper mapper = new StreamingVisionJsonMapper();

    public StreamingVisionHttpAdapter(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        URI uri = URI.create(baseUrl);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Invalid streaming Vision base URL");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        client = RestClient.builder().baseUrl(baseUrl.replaceAll("/+$", "")).requestFactory(factory).build();
    }

    @Override
    public StreamingVisionStatus status(String id) {
        return observedRequest("VISION_STATUS_HTTP", id, () -> {
            var result = mapper.status(get("/vision/status/{id}", id, false).orElseThrow());
            PerformanceLog.result(result.analysisState().name());
            if (result.analysisState() == VisionAnalysisState.COMPLETED || result.analysisState() == VisionAnalysisState.FAILED)
                PerformanceLog.terminal();
            return result;
        });
    }
    @Override
    public Optional<StreamingVisionFinalResult> result(String id) {
        return observedRequest("VISION_RESULT_HTTP", id, () -> {
            var result = get("/vision/result/{id}", id, true).map(mapper::result);
            PerformanceLog.result(result.isPresent() ? "AVAILABLE" : "PENDING");
            if (result.isPresent()) PerformanceLog.terminal();
            return result;
        });
    }

    private <T> T observedRequest(String stage, String id, java.util.function.Supplier<T> request) {
        try (var scope = PerformanceLog.scope(null, null, id, null, null)) {
            return PerformanceLog.measure(stage, () -> {
                try { return request.get(); }
                catch (StreamingVisionException failure) { PerformanceLog.result(failure.code()); throw failure; }
            });
        }
    }

    private Optional<String> get(String path, String id, boolean pendingAllowed) {
        try {
            return client.get().uri(path, id).accept(org.springframework.http.MediaType.APPLICATION_JSON).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                PerformanceLog.httpStatus(status);
                if (status == 202 && pendingAllowed) return Optional.empty();
                if (status == 404) throw new StreamingVisionException("UPSTREAM_NOT_FOUND", false);
                if (status == 408 || status == 429 || status >= 500) throw new StreamingVisionException("UPSTREAM_HTTP_" + status, true);
                if (status != 200) throw new StreamingVisionException("UPSTREAM_HTTP_" + status, false);
                return Optional.of(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
            });
        } catch (ResourceAccessException exception) {
            PerformanceLog.failure(exception);
            PerformanceLog.result("NETWORK_ERROR");
            throw new StreamingVisionException("NETWORK_ERROR", true);
        }
    }
}
