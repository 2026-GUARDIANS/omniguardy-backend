package com.omniguardy.backend.domain.detection.infrastructure.fastapi;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.VisionAnalysisPort;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.domain.detection.infrastructure.fastapi.dto.VisionApiResponse;
import com.omniguardy.backend.global.error.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VisionFastApiAdapter implements VisionAnalysisPort {

    private RestClient restClient;

    @Value("${ai.fastapi.vision.base-url}")
    private String baseUrl;

    @Value("${ai.fastapi.vision.endpoint}")
    private String endpoint;

    @Value("${ai.fastapi.vision.connect-timeout-seconds:5}")
    private long connectTimeoutSeconds;

    @Value("${ai.fastapi.vision.read-timeout-seconds:30}")
    private long readTimeoutSeconds;

    @PostConstruct
    void initializeRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public VisionAnalysis analyze(
            MediaFile file,
            VideoTriggerContext triggerContext
    ) {
        long startedNanos = System.nanoTime();
        boolean success = false;

        MultiValueMap<String, Object> body =
                new LinkedMultiValueMap<>();

        // 영상
        body.add(
                "file",
                resource(file)
        );

        // Trigger ID
        body.add(
                "triggerId",
                triggerContext.triggerId()
        );

        // AUDIO / KEYPAD
        body.add(
                "triggerType",
                triggerContext.triggerType().name()
        );

        // 촬영 트리거 발생 시각
        body.add(
                "triggeredAt",
                triggerContext.triggeredAt()
        );

        /*
         * AUDIO:
         * securityEventId 존재
         *
         * KEYPAD:
         * securityEventId 없음
         */
        if (triggerContext.securityEventId() != null
                && !triggerContext.securityEventId().isBlank()) {

            body.add(
                    "securityEventId",
                    triggerContext.securityEventId()
            );
        }

        try {
            VisionApiResponse response =
                restClient.post()

                        // 중요
                        .uri(baseUrl + endpoint)
                        .header("ngrok-skip-browser-warning", "true")

                        .contentType(
                                MediaType.MULTIPART_FORM_DATA
                        )

                        .body(body)

                        .retrieve()

                        .body(
                                VisionApiResponse.class
                        );

            if (response == null || !"success".equalsIgnoreCase(response.status())
                    || response.result() == null) {
                throw new BusinessException(DetectionErrorCode.VISION_ANALYSIS_FAILED);
            }

            VisionAnalysis analysis = toVisionAnalysis(response, triggerContext);
            success = true;
            return analysis;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(DetectionErrorCode.VISION_ANALYSIS_FAILED, exception);
        } finally {
            log.info("external_api_call api=vision_fastapi eventType={} durationMs={} success={}",
                    triggerContext.triggerType().name(),
                    elapsedMillis(startedNanos), success);
        }
    }

    private double elapsedMillis(long startedNanos) {
        double elapsed = (System.nanoTime() - startedNanos) / 1_000_000.0;
        return Math.round(elapsed * 1_000.0) / 1_000.0;
    }

    private VisionAnalysis toVisionAnalysis(
            VisionApiResponse response,
            VideoTriggerContext triggerContext
    ) {

        VisionApiResponse.Result result =
                response.result();

        // ==============================
        // Behavior
        // KEYPAD에서는 null 가능
        // ==============================

        VisionBehavior behavior = null;

        if (result.behavior() != null) {

            behavior =
                    new VisionBehavior(
                            result.behavior().prediction(),
                            result.behavior().label(),
                            result.behavior().confidence(),
                            result.behavior()
                                    .classProbabilities()
                    );
        }

        // ==============================
        // Video
        // ==============================

        VisionVideoInfo video = null;

        if (result.video() != null) {

            video =
                    new VisionVideoInfo(
                            result.video().fileName(),
                            result.video()
                                    .durationSeconds(),
                            result.video().fps(),
                            result.video().frameCount()
                    );
        }

        // ==============================
        // Observations
        // ==============================

        VisionObservations observations = null;

        if (result.observations() != null) {

            observations =
                    new VisionObservations(
                            result.observations()
                                    .trackedPersonCount(),

                            result.observations()
                                    .hasTracking(),

                            result.observations()
                                    .hasPose(),

                            result.observations()
                                    .analysisMode()
                    );
        }

        // ==============================
        // Vision Events
        // ==============================

        List<VisionEvent> visionEvents =
                result.visionEvents() == null
                        ? List.of()
                        : result.visionEvents()
                        .stream()
                        .map(event ->
                                new VisionEvent(
                                        event.eventType(),
                                        event.confidence(),
                                        event.detectedFrame(),
                                        event.details()
                                )
                        )
                        .toList();

        return new VisionAnalysis(
                triggerContext.triggerId(),
                triggerContext.triggerType(),
                triggerContext.securityEventId(),
                triggerContext.triggeredAt() == null || triggerContext.triggeredAt().isBlank()
                        ? null : OffsetDateTime.parse(triggerContext.triggeredAt()),

                result.analyzedAt(),

                video,
                behavior,
                visionEvents,
                observations
        );
    }

    private org.springframework.core.io.Resource resource(MediaFile file) {
        return new com.omniguardy.backend.domain.detection.infrastructure.fastapi.MediaResource(file);
    }
}
