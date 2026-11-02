package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.port.out.StreamingMediaStoragePort;
import com.omniguardy.backend.domain.visionsession.application.model.StreamingVideoView;
import com.omniguardy.backend.domain.visionsession.application.port.in.StreamingSessionVideoInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import java.time.Duration;
import static com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;

/** Storage only: no Vision, Agent, SecurityEvent writer or notification dependency. */
@Service @RequiredArgsConstructor @Slf4j
public class StreamingSessionVideoService implements StreamingSessionVideoInputPort {
    private final VisionSessionRepository sessions;
    private final KeypadTriggerPort keypad;
    private final VisionAudioTriggerPort audio;
    private final StreamingVideoValidationPort validation;
    private final StreamingMediaStoragePort storage;
    private final StreamingVideoRepository videos;
    @Qualifier("streamingVideoUploadLease") private final Duration streamingVideoUploadLease;

    @Override public Receipt upload(String sessionId, MediaFile file) {
        StreamingSessionVideo claim;
        try {
            var session = session(sessionId);
            String triggerId;
            if (session.triggerType() == VisionTriggerType.KEYPAD) {
                if (!keypad.exists(session.triggerId())) throw new BusinessException(VISION_TRIGGER_NOT_FOUND);
                triggerId = session.triggerId();
            } else triggerId = audio.findIssuedTriggerId(session.eventId())
                    .orElseThrow(() -> new BusinessException(VISION_TRIGGER_NOT_FOUND));
            var info = validation.validate(file);
            var target = storage.planStreamingVideo(sessionId, info.sha256());
            claim = videos.reserve(new VisionBinding(sessionId, session.triggerType(), triggerId, session.eventId()),
                    info, target, streamingVideoUploadLease);
            if (claim.state() == StreamingSessionVideo.State.STORED) return new Receipt(view(claim), false);
        } catch (DataAccessException failure) { throw new BusinessException(STREAMING_VIDEO_DATABASE_FAILED, failure); }

        try {
            var stored = storage.storeStreamingVideo(sessionId, claim.target(), file);
            if (!claim.target().filename().equals(stored.filename()) || !claim.target().path().equals(stored.path()))
                throw new IllegalStateException("Storage returned a different destination");
        } catch (RuntimeException failure) {
            recordFailure(claim, "STORAGE_FAILED");
            throw new BusinessException(STREAMING_VIDEO_STORAGE_FAILED, failure);
        }
        try {
            return new Receipt(view(videos.complete(claim)), true);
        } catch (DataAccessException failure) {
            recordFailure(claim, "DATABASE_FAILED");
            throw new BusinessException(STREAMING_VIDEO_DATABASE_FAILED, failure);
        }
    }

    @Override public StreamingVideoView find(String sessionId) {
        try {
            session(sessionId);
            return view(videos.find(sessionId).orElseThrow(() -> new BusinessException(STREAMING_VIDEO_NOT_FOUND)));
        } catch (DataAccessException failure) { throw new BusinessException(STREAMING_VIDEO_DATABASE_FAILED, failure); }
    }
    private VisionSession session(String id) {
        if (!VisionSession.validId(id, 128)) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        return sessions.findBySessionId(id).orElseThrow(() -> new BusinessException(VISION_SESSION_NOT_FOUND));
    }
    private StreamingVideoView view(StreamingSessionVideo video) {
        return StreamingVideoView.from(video, videos.linkedEventId(video.binding().sessionId()).orElse(null));
    }
    private void recordFailure(StreamingSessionVideo claim, String code) {
        try { videos.failed(claim, code); }
        catch (RuntimeException databaseFailure) {
            log.warn("Streaming video state update failed sessionId={} failureType={}", claim.binding().sessionId(),
                    databaseFailure.getClass().getSimpleName());
            // Original destination/hash remain durable; the same file may reclaim the lease after a restart.
        }
    }
}
