package com.omniguardy.backend.domain.visionsession.infrastructure.streaming;

import com.omniguardy.backend.domain.visionsession.application.usecase.PollStreamingVisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.fastapi.streaming-vision.polling-enabled", havingValue = "true", matchIfMissing = true)
public class StreamingVisionPollScheduler {
    private final PollStreamingVisionService polling;

    @Scheduled(fixedDelayString = "${ai.fastapi.streaming-vision.scheduler-delay-ms:1000}")
    public void poll() { polling.pollDue(); }
}
