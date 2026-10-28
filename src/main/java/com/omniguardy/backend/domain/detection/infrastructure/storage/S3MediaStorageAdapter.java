package com.omniguardy.backend.domain.detection.infrastructure.storage;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.port.out.MediaStoragePort;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Primary
@Profile("prod")
public class S3MediaStorageAdapter implements MediaStoragePort {
    private final S3Client s3;
    private final LocalMediaStorageAdapter local;
    private final String bucket;
    private final String prefix;
    public S3MediaStorageAdapter(S3Client s3, LocalMediaStorageAdapter local,
            @Value("${aws.s3.bucket}") String bucket, @Value("${aws.s3.video-prefix}") String prefix) {
        this.s3 = s3; this.local = local; this.bucket = bucket;
        this.prefix = prefix.replaceAll("^/+|/+$", "");
        if (bucket.isBlank() || this.prefix.isBlank() || this.prefix.contains(".."))
            throw new IllegalArgumentException("S3_BUCKET_NAME and a valid S3_VIDEO_PREFIX are required");
    }
    @Override public StoredMedia saveAudio(String eventId, MediaFile file) { return local.saveAudio(eventId, file); }
    @Override public StoredMedia saveVideo(String eventId, MediaFile file) {
        String digest = file.sha256();
        String filename = UUID.nameUUIDFromBytes(eventId.getBytes(StandardCharsets.UTF_8)) + "-" + digest + ".mp4";
        String key = prefix + "/" + filename;
        try {
            // Deterministic key and conditional PUT avoid extra objects (including versioned buckets) on retry.
            var request = PutObjectRequest.builder().bucket(bucket).key(key).ifNoneMatch("*")
                    .contentType(file.contentType() == null ? "application/octet-stream" : file.contentType())
                    .serverSideEncryption(ServerSideEncryption.AES256).build();
            RequestBody body = RequestBody.fromContentProvider(() -> {
                try { return file.openStream(); }
                catch (java.io.IOException e) { throw new UncheckedIOException(e); }
            }, file.size(), request.contentType());
            s3.putObject(request, body);
            return new StoredMedia(filename, key);
        } catch (S3Exception e) {
            if (e.statusCode() == 412) return new StoredMedia(filename, key);
            throw new BusinessException(DetectionErrorCode.MEDIA_STORAGE_FAILED, e);
        } catch (RuntimeException e) {
            throw new BusinessException(DetectionErrorCode.MEDIA_STORAGE_FAILED, e);
        }
    }
}
