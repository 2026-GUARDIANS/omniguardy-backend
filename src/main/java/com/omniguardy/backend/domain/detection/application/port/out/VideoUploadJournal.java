package com.omniguardy.backend.domain.detection.application.port.out;

import com.omniguardy.backend.domain.detection.application.model.*;

/** Durable checkpoints for production video uploads, including normal KEYPAD videos without events. */
public interface VideoUploadJournal {
    record Upload(String id, VisionAnalysis vision, MediaStoragePort.StoredMedia stored, VideoReceipt completed) {}
    Upload begin(MediaFile file, VideoTriggerContext context);
    void analyzed(Upload upload, VisionAnalysis vision);
    void stored(Upload upload, MediaStoragePort.StoredMedia stored);
    void complete(Upload upload, VideoReceipt result);
    void release(Upload upload);
}
