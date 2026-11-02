package com.omniguardy.backend.domain.visionsession.infrastructure.fallback;
import com.omniguardy.backend.domain.visionsession.application.model.VisionFallbackPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.time.Duration;
@Configuration
public class VisionFallbackConfiguration {
    @Bean VisionFallbackPolicy visionFallbackPolicy(
            @Value("${omniguardy.vision-fallback.retry-delay:10s}") Duration retry,
            @Value("${omniguardy.vision-fallback.video-wait:10m}") Duration wait,
            @Value("${omniguardy.vision-fallback.vision-lease:5m}") Duration vision,
            @Value("${omniguardy.vision-fallback.agent-lease:5m}") Duration agent,
            @Value("${omniguardy.vision-fallback.max-vision-attempts:3}") int attempts,
            @Value("${omniguardy.vision-fallback.max-preparation-attempts:3}") int preparationAttempts,
            @Value("${omniguardy.vision-fallback.batch-size:10}") int batch,
            @Value("${ai.fastapi.vision.connect-timeout-seconds:5}") long connect,
            @Value("${ai.fastapi.vision.read-timeout-seconds:180}") long read) {
        if (vision.compareTo(Duration.ofSeconds(connect + read + 5)) <= 0)
            throw new IllegalArgumentException("Fallback Vision lease must exceed the MP4 HTTP timeout budget");
        return new VisionFallbackPolicy(retry, wait, vision, agent, attempts, preparationAttempts, batch);
    }
    @Bean(name="visionFallbackTaskScheduler") ThreadPoolTaskScheduler fallbackScheduler() {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("vision-fallback-"); return scheduler;
    }
}

