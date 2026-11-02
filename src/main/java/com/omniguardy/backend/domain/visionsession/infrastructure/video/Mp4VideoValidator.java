package com.omniguardy.backend.domain.visionsession.infrastructure.video;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.application.model.StreamingVideoFileInfo;
import com.omniguardy.backend.domain.visionsession.application.port.out.StreamingVideoValidationPort;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode.*;

/** Structural ISO-BMFF validation only: no decoding, transcoding or AI analysis. */
@Component
public class Mp4VideoValidator implements StreamingVideoValidationPort {
    private static final Set<String> BRANDS = Set.of("isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash");
    @Override public StreamingVideoFileInfo validate(MediaFile file) {
        if (file == null || file.size() == 0) throw new BusinessException(EMPTY_STREAMING_VIDEO);
        if (file.size() > StreamingVideoFileInfo.MAX_BYTES) throw new BusinessException(STREAMING_VIDEO_TOO_LARGE);
        String name = file.originalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".mp4")
                || file.contentType() == null || !file.contentType().split(";", 2)[0].trim().equalsIgnoreCase("video/mp4")) throw invalid();
        try (InputStream input = new BufferedInputStream(file.openStream())) {
            var state = new State();
            boxes(input, file.size(), "root", state);
            if (state.ftyp != 1 || state.moov != 1 || !state.video || !state.media) throw invalid();
        } catch (IOException exception) { throw new BusinessException(INVALID_STREAMING_MP4, exception); }
        name = name.replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        if (name.length() > 255) name = name.substring(0, 251) + ".mp4";
        return new StreamingVideoFileInfo(file.sha256(), file.size(), name);
    }

    private void boxes(InputStream input, long remaining, String parent, State state) throws IOException {
        while (remaining > 0) {
            if (remaining < 8 || ++state.boxes > 100_000) throw invalid();
            byte[] header = exact(input, 8);
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(header, 0, 4).getInt());
            String type = new String(header, 4, 4, StandardCharsets.US_ASCII);
            int headerSize = 8;
            if (size == 1) { size = ByteBuffer.wrap(exact(input, 8)).getLong(); headerSize = 16; }
            else if (size == 0) size = remaining;
            if (size < headerSize || size > remaining) throw invalid();
            long payload = size - headerSize;
            if (parent.equals("root") && type.equals("ftyp")) {
                if (payload < 8 || payload > 4096 || payload % 4 != 0) throw invalid();
                byte[] brands = exact(input, (int) payload); boolean compatible = false;
                for (int index = 0; index < brands.length; index += 4) {
                    if (index != 4 && BRANDS.contains(new String(brands, index, 4, StandardCharsets.US_ASCII))) compatible = true;
                }
                if (!compatible) throw invalid(); state.ftyp++;
            } else if ((parent.equals("root") && type.equals("moov"))
                    || (parent.equals("moov") && type.equals("trak")) || (parent.equals("trak") && type.equals("mdia"))) {
                if (type.equals("moov")) state.moov++;
                boxes(input, payload, type, state);
            } else if (parent.equals("mdia") && type.equals("hdlr")) {
                if (payload < 12) throw invalid();
                byte[] handler = exact(input, 12);
                if (new String(handler, 8, 4, StandardCharsets.US_ASCII).equals("vide")) state.video = true;
                input.skipNBytes(payload - 12);
            } else {
                if (parent.equals("root") && type.equals("mdat") && payload > 0) state.media = true;
                input.skipNBytes(payload);
            }
            remaining -= size;
        }
    }
    private static byte[] exact(InputStream input, int size) throws IOException {
        byte[] bytes = input.readNBytes(size); if (bytes.length != size) throw invalid(); return bytes;
    }
    private static BusinessException invalid() { return new BusinessException(INVALID_STREAMING_MP4); }
    private static class State { int boxes, ftyp, moov; boolean video, media; }
}
