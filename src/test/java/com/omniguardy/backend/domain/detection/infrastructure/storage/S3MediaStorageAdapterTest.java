package com.omniguardy.backend.domain.detection.infrastructure.storage;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class S3MediaStorageAdapterTest {
    private final S3Client s3 = mock(S3Client.class);
    private final LocalMediaStorageAdapter local = mock(LocalMediaStorageAdapter.class);
    private final S3MediaStorageAdapter adapter = new S3MediaStorageAdapter(s3, local, "test-bucket", "videos");
    @TempDir Path temp;

    @Test void streamsFromDiskAndUsesStableConditionalPrivateObject() throws Exception {
        Path path = temp.resolve("video"); Files.write(path, new byte[]{1,2,3});
        var file = MediaFile.fromPath("../../video.mp4", "video/mp4", path);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(call -> {
            PutObjectRequest req = call.getArgument(0); RequestBody body = call.getArgument(1);
            assertEquals("*", req.ifNoneMatch());
            assertNull(req.acl());
            assertEquals(ServerSideEncryption.AES256, req.serverSideEncryption());
            try (var in = body.contentStreamProvider().newStream()) { assertArrayEquals(new byte[]{1,2,3}, in.readAllBytes()); }
            return PutObjectResponse.builder().build();
        });
        var first = adapter.saveVideo("trigger", file);
        assertEquals(first, adapter.saveVideo("trigger", file));
        assertTrue(first.path().startsWith("videos/")); assertFalse(first.path().contains(".."));
        verifyNoInteractions(local);
    }
    @Test void alreadyStoredObjectIsReused() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(412).build());
        assertNotNull(adapter.saveVideo("id", new MediaFile("v", null, new byte[]{1})).path());
    }
    @Test void deniedUploadFailsWithoutLocalVideoFallback() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());
        assertThrows(BusinessException.class, () -> adapter.saveVideo("id", new MediaFile("v", null, new byte[]{1})));
        verifyNoInteractions(local);
    }
    @Test void audioUsesExistingLocalStorage() {
        var file = new MediaFile("a.wav", "audio/wav", new byte[]{1});
        adapter.saveAudio("id", file);
        verify(local).saveAudio("id", file); verifyNoInteractions(s3);
    }
}
