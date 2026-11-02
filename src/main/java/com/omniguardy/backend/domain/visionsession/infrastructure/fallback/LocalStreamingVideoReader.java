package com.omniguardy.backend.domain.visionsession.infrastructure.fallback;
import com.omniguardy.backend.domain.detection.application.port.out.StreamingMediaStoragePort;
import com.omniguardy.backend.domain.visionsession.application.port.out.StoredStreamingVideoReader;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingSessionVideo;
import com.omniguardy.backend.domain.visionsession.domain.error.VisionFallbackException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.io.*;
@Component @Profile("!prod") @RequiredArgsConstructor
public class LocalStreamingVideoReader implements StoredStreamingVideoReader {
    private final StreamingMediaStoragePort storage;
    @Override public Download read(StreamingSessionVideo video) {
        if(video.state()!=StreamingSessionVideo.State.STORED || !video.target().equals(storage.planStreamingVideo(video.binding().sessionId(),video.sha256())))
            throw new VisionFallbackException("VIDEO_STORAGE_BINDING_MISMATCH",true);
        try { return VerifiedVideoDownload.copy(video,Files.newInputStream(Path.of(video.target().path()))); }
        catch(IOException e) { throw new UncheckedIOException(e); }
    }
}

