package com.omniguardy.backend.domain.visionsession.application.port.in;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.application.model.StreamingVideoView;

public interface StreamingSessionVideoInputPort {
    record Receipt(StreamingVideoView video, boolean storedNow) {}
    Receipt upload(String sessionId, MediaFile file);
    StreamingVideoView find(String sessionId);
}
