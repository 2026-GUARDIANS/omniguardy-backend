package com.omniguardy.backend.domain.detection.infrastructure.fastapi;

import com.omniguardy.backend.domain.detection.application.model.AudioAnalysis;
import com.omniguardy.backend.domain.detection.application.model.AudioEventType;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.port.out.AudioAnalysisPort;
import com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto.AudioPredictResponseDto;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AudioFastApiAdapter implements AudioAnalysisPort {
    private RestClient restClient;
    private boolean injectedClient;
    @Value("${ai.fastapi.audio.connect-timeout-seconds:5}") private long connectTimeout;
    @Value("${ai.fastapi.audio.read-timeout-seconds:60}") private long readTimeout;
    @Value("${ai.fastapi.audio.base-url}") private String baseUrl;
    @Value("${ai.fastapi.audio.endpoint}") private String endpoint;

    public AudioFastApiAdapter() {
        this.restClient = RestClient.create();
    }

    AudioFastApiAdapter(RestClient restClient) {
        this.restClient = restClient;
        this.injectedClient = true;
    }

    @jakarta.annotation.PostConstruct
    void initializeClient() {
        if (injectedClient) return;
        var http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(connectTimeout)).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(java.time.Duration.ofSeconds(readTimeout));
        restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public AudioAnalysis analyze(MediaFile file) {
        long startedNanos = System.nanoTime();
        boolean success = false;
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", resource(file)).contentType(MediaType.parseMediaType("audio/wav"));
            AudioPredictResponseDto response = restClient.post().uri(baseUrl + endpoint)
                    .header("ngrok-skip-browser-warning", "true")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body.build()).retrieve()
                    .body(AudioPredictResponseDto.class);
            if (response == null) throw new BusinessException(DetectionErrorCode.AUDIO_ANALYSIS_FAILED);
            AudioEventType eventType = AudioEventType.fromPredictedClass(response.getPredictedClass());
            AudioAnalysis analysis = new AudioAnalysis(eventType, response.getPredictedProbability());
            success = true;
            return analysis;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new BusinessException(DetectionErrorCode.AUDIO_ANALYSIS_FAILED, exception);
        } finally {
            log.info("external_api_call api=audio_fastapi eventType=AUDIO durationMs={} success={}",
                    elapsedMillis(startedNanos), success);
        }
    }

    private double elapsedMillis(long startedNanos) {
        double elapsed = (System.nanoTime() - startedNanos) / 1_000_000.0;
        return Math.round(elapsed * 1_000.0) / 1_000.0;
    }

    private org.springframework.core.io.Resource resource(MediaFile file) {
        return new com.omniguardy.backend.domain.detection.infrastructure.fastapi.MediaResource(file);
    }
}
