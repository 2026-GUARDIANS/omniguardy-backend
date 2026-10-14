package com.omniguardy.backend.domain.detection.application.model;

public record AudioReceipt(
        String eventId,
        String filename,
        long size,
        AudioEventType eventType,
        double probability
) {}

