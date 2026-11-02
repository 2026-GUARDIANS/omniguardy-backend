package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget;
import com.omniguardy.backend.domain.visionsession.application.model.StreamingVideoFileInfo;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import java.time.Duration;
import java.util.Optional;

public interface StreamingVideoRepository {
    StreamingSessionVideo reserve(VisionBinding binding, StreamingVideoFileInfo file, VideoStorageTarget target, Duration lease);
    StreamingSessionVideo complete(StreamingSessionVideo claim);
    void failed(StreamingSessionVideo claim, String reason);
    Optional<StreamingSessionVideo> find(String sessionId);
    /** A live read follows V9 event creation without altering V9 or the SecurityEvent. */
    Optional<String> linkedEventId(String sessionId);
}
