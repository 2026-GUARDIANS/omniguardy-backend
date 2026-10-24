package com.omniguardy.backend.domain.detection.application.model;

public record AudioDetectionResult(String eventId, Outcome outcome, String filename) {
    public enum Outcome { TRIGGERED, BACKGROUND_IGNORED, COOLDOWN_SUPPRESSED, DUPLICATE }
}
