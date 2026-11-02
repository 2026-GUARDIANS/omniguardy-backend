package com.omniguardy.backend.domain.detection.application.port.out;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget;

/** Storage-only extension; the existing LEGACY MediaStoragePort contract is unchanged. */
public interface StreamingMediaStoragePort {
    VideoStorageTarget planStreamingVideo(String sessionId, String sha256);
    MediaStoragePort.StoredMedia storeStreamingVideo(String sessionId, VideoStorageTarget target, MediaFile file);
}
