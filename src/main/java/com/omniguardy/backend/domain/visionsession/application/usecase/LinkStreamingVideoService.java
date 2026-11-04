package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.visionsession.application.port.in.LinkStreamingVideoInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVideoEventLinkPort.Result.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode.*;

/** Metadata attachment only; never starts analysis, creates events, or delivers responses. */
@Service @RequiredArgsConstructor @Slf4j
public class LinkStreamingVideoService implements LinkStreamingVideoInputPort {
    private final StreamingVideoLinkRepository videos;
    private final StreamingVideoEventLinkPort events;
    @Value("${omniguardy.streaming-video.event-link-batch-size:100}") private int batchSize = 100;
    private String cursor = "";

    @Override public void afterUpload(String sessionId) {
        StreamingVideoEventLinkPort.Result result;
        try { result = link(sessionId); }
        catch (BusinessException invalid) { throw invalid; }
        catch (RuntimeException failure) { deferred(sessionId, failure); return; }
        if (result == CONFLICT) throw new BusinessException(STREAMING_VIDEO_EVENT_CONFLICT);
        if (result == INVALID_BINDING) throw new BusinessException(STREAMING_VIDEO_BINDING_MISMATCH);
    }

    @Override public void afterEvent(String sessionId) {
        try {
            var result = link(sessionId);
            if (result == CONFLICT || result == INVALID_BINDING)
                log.warn("Streaming video link rejected sessionId={} result={}", sessionId, result);
        } catch (RuntimeException failure) { deferred(sessionId, failure); }
    }

    @Override public synchronized void reconcile() {
        // Keyset scanning includes old rows and cannot be starved by a conflicting/unbound row.
        var ids = videos.storedSessionsAfter(cursor, Math.max(1, batchSize));
        for (String id : ids) { afterEvent(id); cursor = id; }
        if (ids.size() < Math.max(1, batchSize)) cursor = "";
    }

    private StreamingVideoEventLinkPort.Result link(String id) {
        return videos.findVerified(id).map(events::link).orElse(NOT_READY);
    }
    private void deferred(String id, RuntimeException failure) {
        log.warn("Streaming video link deferred sessionId={} failureType={}", id, failure.getClass().getSimpleName());
    }
}
