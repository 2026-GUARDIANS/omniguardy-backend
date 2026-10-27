package com.omniguardy.backend.domain.detection.infrastructure.fastapi;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import org.springframework.core.io.AbstractResource;
import java.io.IOException;
import java.io.InputStream;

/** Repeatable multipart content without loading an entire video into heap. */
public class MediaResource extends AbstractResource {
    private final MediaFile file;
    public MediaResource(MediaFile file) { this.file = file; }
    @Override public String getDescription() { return "request media"; }
    @Override public String getFilename() { return file.originalFilename(); }
    @Override public long contentLength() { return file.size(); }
    @Override public InputStream getInputStream() throws IOException { return file.openStream(); }
}
