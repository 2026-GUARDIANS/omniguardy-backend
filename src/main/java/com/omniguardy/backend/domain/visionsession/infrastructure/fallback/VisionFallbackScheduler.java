package com.omniguardy.backend.domain.visionsession.infrastructure.fallback;
import com.omniguardy.backend.domain.visionsession.application.usecase.ProcessVisionFallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="omniguardy.vision-fallback.enabled",havingValue="true")
public class VisionFallbackScheduler {
    private final ProcessVisionFallbackService worker;
    @Scheduled(fixedDelayString="${omniguardy.vision-fallback.scheduler-delay-ms:1000}",scheduler="visionFallbackTaskScheduler")
    public void process() { worker.processDue(); }
}

