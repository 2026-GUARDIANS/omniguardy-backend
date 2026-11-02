package com.omniguardy.backend.domain.visionsession.application.model;

public record StreamingVideoFileInfo(String sha256, long sizeBytes, String originalFilename) {
    public static final long MAX_BYTES = 100L * 1024 * 1024;
}
