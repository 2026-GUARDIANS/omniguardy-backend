package com.omniguardy.backend.domain.detection.infrastructure.storage;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreamingMediaStorageTest {
    @Test void streamingS3ReusesStableConditionalPutAndHasSeparateLegacyIdentity() throws Exception {
        var s3 = mock(S3Client.class); var local = mock(LocalMediaStorageAdapter.class);
        var storage = new S3MediaStorageAdapter(s3, local, "test-bucket", "videos");
        var file = new MediaFile("../../recording.mp4", "video/mp4", new byte[]{1,2,3});
        var objects = new HashMap<String, byte[]>();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
            PutObjectRequest request = call.getArgument(0); RequestBody body = call.getArgument(1);
            assertEquals("*", request.ifNoneMatch()); assertEquals(ServerSideEncryption.AES256, request.serverSideEncryption());
            if (objects.containsKey(request.key())) throw S3Exception.builder().statusCode(412).build();
            try (var input = body.contentStreamProvider().newStream()) { objects.put(request.key(), input.readAllBytes()); }
            return PutObjectResponse.builder().build();
        });
        var target = storage.planStreamingVideo("session", file.sha256());
        assertEquals(storage.storeStreamingVideo("session", target, file), storage.storeStreamingVideo("session", target, file));
        assertEquals(1, objects.size()); assertArrayEquals(file.bytes(), objects.get(target.path()));
        assertNotEquals(storage.saveVideo("session", file).path(), target.path()); verifyNoInteractions(local);
    }
    @Test void changedS3DestinationCannotCreateAnObjectElsewhereOnRecovery() {
        var s3 = mock(S3Client.class); var local = mock(LocalMediaStorageAdapter.class);
        var old = new S3MediaStorageAdapter(s3, local, "old-bucket", "videos");
        var current = new S3MediaStorageAdapter(s3, local, "new-bucket", "videos");
        var file = new MediaFile("v.mp4", "video/mp4", new byte[]{1});
        assertThrows(IllegalStateException.class, () -> current.storeStreamingVideo("session", old.planStreamingVideo("session", file.sha256()), file));
        verifyNoInteractions(s3);
    }
    @Test void s3FailureNeverFallsBackToLocalOrAnalysis() {
        var s3 = mock(S3Client.class); var local = mock(LocalMediaStorageAdapter.class);
        var storage = new S3MediaStorageAdapter(s3, local, "test-bucket", "videos");
        var file = new MediaFile("v.mp4", "video/mp4", new byte[]{1});
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        assertThrows(BusinessException.class, () -> storage.storeStreamingVideo("session", storage.planStreamingVideo("session", file.sha256()), file));
        verifyNoInteractions(local);
    }
    @Test void localConcurrentRetriesPublishOneCompleteOriginalAndDoNotOverwriteOtherContent() throws Exception {
        var storage = new LocalMediaStorageAdapter(); var file = new MediaFile("../../v.mp4", "video/mp4", new byte[]{1,2,3});
        String session = "test-" + UUID.randomUUID(); VideoStorageTarget target = storage.planStreamingVideo(session, file.sha256());
        var path = Path.of(target.path()); var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Callable<Object>>();
            for (int i = 0; i < 4; i++) tasks.add(() -> storage.storeStreamingVideo(session, target, file));
            for (var future : pool.invokeAll(tasks, 10, TimeUnit.SECONDS)) assertNotNull(future.get());
            assertArrayEquals(file.bytes(), Files.readAllBytes(path));
            Files.write(path, new byte[]{9});
            assertThrows(BusinessException.class, () -> storage.storeStreamingVideo(session, target, file));
            assertArrayEquals(new byte[]{9}, Files.readAllBytes(path));
        } finally { pool.shutdownNow(); Files.deleteIfExists(path); }
    }
}
