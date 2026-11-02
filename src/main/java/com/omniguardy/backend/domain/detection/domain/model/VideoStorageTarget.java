package com.omniguardy.backend.domain.detection.domain.model;

/** Durable storage destination, independent of a specific client or storage adapter. */
public record VideoStorageTarget(String storageType, String namespace, String filename, String path) {
    public VideoStorageTarget {
        if (!java.util.Set.of("S3", "LOCAL").contains(storageType) || namespace == null || namespace.isBlank()
                || namespace.length() > 500 || filename == null || filename.isBlank() || filename.length() > 255
                || path == null || path.isBlank() || path.length() > 1000)
            throw new IllegalArgumentException("Invalid streaming video destination");
    }
}
