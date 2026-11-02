package com.omniguardy.backend.domain.visionsession.application.port.out;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingSessionVideo;
import java.nio.file.*;
import java.io.IOException;
public interface StoredStreamingVideoReader {
    record Download(MediaFile file, Path temporary) implements AutoCloseable {
        @Override public void close() throws IOException { if (temporary != null) Files.deleteIfExists(temporary); }
    }
    Download read(StreamingSessionVideo video);
}

