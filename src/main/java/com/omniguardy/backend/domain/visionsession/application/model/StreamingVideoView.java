package com.omniguardy.backend.domain.visionsession.application.model;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import java.time.Instant;

/** Contains storage metadata, never an expiring URL or a lease credential. */
public record StreamingVideoView(String sessionId, VisionTriggerType triggerType, String triggerId, String securityEventId,
        StreamingSessionVideo.State uploadState, String sha256, long sizeBytes, String originalFilename, String filename,
        String storageType, String storageNamespace, String storagePath, Instant createdAt, Instant storedAt, String lastError) {
    public static StreamingVideoView from(StreamingSessionVideo video, String linkedEventId) {
        return new StreamingVideoView(video.binding().sessionId(), video.binding().triggerType(), video.binding().triggerId(),
                linkedEventId, video.state(), video.sha256(), video.sizeBytes(), video.originalFilename(), video.target().filename(),
                video.target().storageType(), video.target().namespace(), video.target().path(), video.createdAt(), video.storedAt(), video.lastError());
    }
}
