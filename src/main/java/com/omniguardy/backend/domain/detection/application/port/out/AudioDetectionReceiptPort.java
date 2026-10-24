package com.omniguardy.backend.domain.detection.application.port.out;

public interface AudioDetectionReceiptPort {
    // Must participate in the event transaction and enforce uniqueness before side effects.
    void claim(String detectionId);
}
