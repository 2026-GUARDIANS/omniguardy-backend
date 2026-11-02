package com.omniguardy.backend.domain.visionsession.domain.model;

import com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget;
import java.time.Instant;

public record StreamingSessionVideo(VisionBinding binding, String sha256, long sizeBytes, String originalFilename,
        VideoStorageTarget target, State state, String leaseToken, Instant leaseUntil, int attempts,
        Instant createdAt, Instant storedAt, String lastError) {
    public enum State { UPLOADING, STORED, FAILED }
}
