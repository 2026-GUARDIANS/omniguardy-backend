package com.omniguardy.backend.domain.visionsession.infrastructure.streaming;

import com.omniguardy.backend.domain.visionsession.application.model.VisionPollingPolicy;
import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVisionQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.*;

@Configuration
@EnableScheduling
public class StreamingVisionConfiguration {
    @Bean Clock visionStreamingClock() { return Clock.systemUTC(); }

    @Bean
    VisionPollingPolicy visionPollingPolicy(
            @Value("${ai.fastapi.streaming-vision.poll-interval:2s}") Duration interval,
            @Value("${ai.fastapi.streaming-vision.max-wait:5m}") Duration maxWait,
            @Value("${ai.fastapi.streaming-vision.lease-duration:30s}") Duration lease,
            @Value("${ai.fastapi.streaming-vision.batch-size:10}") int batchSize,
            @Value("${ai.fastapi.streaming-vision.connect-timeout:2s}") Duration connect,
            @Value("${ai.fastapi.streaming-vision.read-timeout:5s}") Duration read) {
        if (connect.toMillis() < 1 || read.toMillis() < 1 || lease.compareTo(connect.plus(read).multipliedBy(2).plusSeconds(1)) <= 0)
            throw new IllegalArgumentException("Vision lease must exceed two HTTP request timeout budgets plus one second");
        return new VisionPollingPolicy(interval, maxWait, lease, batchSize);
    }

    @Bean
    StreamingVisionQueryPort streamingVisionQueryPort(
            @Value("${ai.fastapi.streaming-vision.base-url:http://localhost:8010}") String baseUrl,
            @Value("${ai.fastapi.streaming-vision.connect-timeout:2s}") Duration connect,
            @Value("${ai.fastapi.streaming-vision.read-timeout:5s}") Duration read) {
        return new StreamingVisionHttpAdapter(baseUrl, connect, read);
    }
}
