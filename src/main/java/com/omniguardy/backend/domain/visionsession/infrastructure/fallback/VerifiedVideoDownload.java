package com.omniguardy.backend.domain.visionsession.infrastructure.fallback;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.application.port.out.StoredStreamingVideoReader.Download;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingSessionVideo;
import com.omniguardy.backend.domain.visionsession.domain.error.VisionFallbackException;
import java.io.*;
import java.nio.file.*;
final class VerifiedVideoDownload {
    private VerifiedVideoDownload() {}
    static Download copy(StreamingSessionVideo video,InputStream input) throws IOException {
        Path temporary=null;
        try(input) {
            temporary=Files.createTempFile("omniguardy-fallback-",".mp4");
            try(var output=Files.newOutputStream(temporary)) {
                byte[] buffer=new byte[65536]; long size=0;
                for(int read;(read=input.read(buffer))!=-1;) {
                    size+=read;
                    if(size>video.sizeBytes()) throw new VisionFallbackException("VIDEO_CONTENT_MISMATCH",true);
                    output.write(buffer,0,read);
                }
            }
            var file=MediaFile.fromPath(video.originalFilename(),"video/mp4",temporary);
            if(file.size()!=video.sizeBytes() || !file.sha256().equals(video.sha256())) throw new VisionFallbackException("VIDEO_CONTENT_MISMATCH",true);
            return new Download(file,temporary);
        } catch(IOException|RuntimeException failure) {
            if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}

