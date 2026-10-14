package com.omniguardy.backend.domain.detection.application.port.out;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
public interface MediaStoragePort {
    StoredMedia saveAudio(String eventId, MediaFile file);
    StoredMedia saveVideo(String eventId, MediaFile file);
    record StoredMedia(String filename, String path) {}
}

