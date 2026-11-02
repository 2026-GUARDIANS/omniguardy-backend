package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.port.out.StreamingMediaStoragePort;
import com.omniguardy.backend.domain.detection.infrastructure.storage.LocalMediaStorageAdapter;
import com.omniguardy.backend.domain.detection.domain.model.VideoStorageTarget;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.domain.visionsession.domain.error.VisionFallbackException;
import com.omniguardy.backend.domain.visionsession.infrastructure.fallback.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoredStreamingVideoReaderTest {
    @Test void localReaderMakesVerifiedFileBackedCopyAndPreservesOriginal() throws Exception {
        var storage=new LocalMediaStorageAdapter(); String id="reader-test-"+UUID.randomUUID(); var file=StreamingMp4TestSupport.file(1);
        var target=storage.planStreamingVideo(id,file.sha256()); storage.storeStreamingVideo(id,target,file);
        try {
            Path temporary;
            try(var download=new LocalStreamingVideoReader(storage).read(video(id,file,target))) {
                temporary=download.temporary(); assertArrayEquals(file.bytes(),download.file().bytes()); assertNotEquals(Path.of(target.path()),temporary);
            }
            assertFalse(Files.exists(temporary)); assertArrayEquals(file.bytes(),Files.readAllBytes(Path.of(target.path())));
        } finally { Files.deleteIfExists(Path.of(target.path())); }
    }
    @Test void s3ReaderRejectsChangedDestinationAndOversizedOrCorruptContent() {
        var s3=mock(S3Client.class); var storage=mock(StreamingMediaStoragePort.class);
        var file=StreamingMp4TestSupport.file(1); var target=new VideoStorageTarget("S3","bucket","v.mp4","videos/v.mp4");
        when(storage.planStreamingVideo("session",file.sha256())).thenReturn(target);
        var reader=new S3StreamingVideoReader(s3,storage);
        for(byte[] data:new byte[][]{new byte[(int)file.size()+1],new byte[(int)file.size()],new byte[1]}) {
            doReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(),AbortableInputStream.create(new ByteArrayInputStream(data))))
                    .when(s3).getObject(any(GetObjectRequest.class));
            assertEquals("VIDEO_CONTENT_MISMATCH",assertThrows(VisionFallbackException.class,() -> reader.read(video("session",file,target))).getMessage());
        }
        reset(s3);
        when(storage.planStreamingVideo("session",file.sha256())).thenReturn(new VideoStorageTarget("S3","other","v.mp4","videos/v.mp4"));
        assertThrows(VisionFallbackException.class,() -> reader.read(video("session",file,target))); verifyNoInteractions(s3);
    }
    @Test void classifierSeparatesPermanentHttpContractFromRetryableNetworkFailures() {
        var classifier=new FallbackFailureClassifier();
        assertFalse(classifier.permanent(new UncheckedIOException(new java.net.SocketTimeoutException())));
        assertFalse(classifier.permanent(S3Exception.builder().statusCode(503).build()));
        assertFalse(classifier.permanent(S3Exception.builder().statusCode(404).build()));
        assertTrue(classifier.permanent(S3Exception.builder().statusCode(403).build()));
        assertTrue(classifier.permanent(new VisionFallbackException("INVALID_MP4_RESULT_CONTRACT",true)));
        assertTrue(classifier.permanent(org.springframework.web.client.HttpClientErrorException.create(org.springframework.http.HttpStatus.BAD_REQUEST,"bad",org.springframework.http.HttpHeaders.EMPTY,new byte[0],null)));
        assertFalse(classifier.permanent(org.springframework.web.client.HttpServerErrorException.create(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"test",org.springframework.http.HttpHeaders.EMPTY,new byte[0],null)));
    }
    StreamingSessionVideo video(String id,MediaFile file,VideoStorageTarget target) {
        return new StreamingSessionVideo(new VisionBinding(id,VisionTriggerType.KEYPAD,"trigger",null),file.sha256(),file.size(),file.originalFilename(),target,
                StreamingSessionVideo.State.STORED,null,null,1,Instant.now(),Instant.now(),null);
    }
}
