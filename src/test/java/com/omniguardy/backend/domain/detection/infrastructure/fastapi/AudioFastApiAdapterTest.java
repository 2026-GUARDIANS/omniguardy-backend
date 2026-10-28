package com.omniguardy.backend.domain.detection.infrastructure.fastapi;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AudioFastApiAdapterTest {
    private final MediaFile file = new MediaFile("audio.wav", "audio/wav", new byte[]{1});
    private AudioFastApiAdapter adapter;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = configuredAdapter(builder.build());
    }

    @Test
    void convertsHttpFailureToBusinessException() {
        server.expect(requestTo("http://audio.test/predict"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.analyze(file));

        assertEquals(DetectionErrorCode.AUDIO_ANALYSIS_FAILED, exception.getErrorCode());
        server.verify();
    }

    @Test
    void convertsTransportFailureToBusinessException() {
        RestClient restClient = RestClient.builder()
                .requestInterceptor((request, body, execution) -> {
                    throw new ResourceAccessException("connection failed");
                })
                .build();
        AudioFastApiAdapter failingAdapter = configuredAdapter(restClient);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> failingAdapter.analyze(file));

        assertEquals(DetectionErrorCode.AUDIO_ANALYSIS_FAILED, exception.getErrorCode());
        assertInstanceOf(ResourceAccessException.class, exception.getCause());
    }

    private AudioFastApiAdapter configuredAdapter(RestClient restClient) {
        AudioFastApiAdapter configured = new AudioFastApiAdapter(restClient);
        ReflectionTestUtils.setField(configured, "baseUrl", "http://audio.test");
        ReflectionTestUtils.setField(configured, "endpoint", "/predict");
        return configured;
    }
}
