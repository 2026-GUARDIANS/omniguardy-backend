package com.omniguardy.backend.domain.detection.application.model;

public record MediaFile(String originalFilename, String contentType, byte[] bytes) {
    public MediaFile {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }
    public long size() { return bytes.length; }
    @Override public byte[] bytes() { return bytes.clone(); }
}

