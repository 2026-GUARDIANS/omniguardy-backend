package com.omniguardy.backend.domain.visionsession.infrastructure.video;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.Duration;

@Configuration
public class StreamingVideoConfiguration {
    @Bean Duration streamingVideoUploadLease(@Value("${omniguardy.streaming-video.upload-lease:5m}") Duration lease) {
        if (lease.toMillis() < 1) throw new IllegalArgumentException("Streaming video upload lease must be positive");
        return lease;
    }
}
