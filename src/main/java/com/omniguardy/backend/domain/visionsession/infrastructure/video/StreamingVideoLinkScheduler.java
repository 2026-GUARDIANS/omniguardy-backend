package com.omniguardy.backend.domain.visionsession.infrastructure.video;

import com.omniguardy.backend.domain.visionsession.application.port.in.LinkStreamingVideoInputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "omniguardy.streaming-video.event-link-enabled", havingValue = "true", matchIfMissing = true)
public class StreamingVideoLinkScheduler {
    private final LinkStreamingVideoInputPort links;
    @Scheduled(initialDelayString = "${omniguardy.streaming-video.event-link-delay-ms:30000}",
            fixedDelayString = "${omniguardy.streaming-video.event-link-delay-ms:30000}", scheduler = "streamingVideoLinkTaskScheduler")
    public void reconcile() {
        try { links.reconcile(); }
        catch (RuntimeException failure) {
            log.warn("Streaming video reconciliation deferred failureType={}", failure.getClass().getSimpleName());
        }
    }
}
