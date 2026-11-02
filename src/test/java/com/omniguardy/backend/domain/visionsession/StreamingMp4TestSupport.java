package com.omniguardy.backend.domain.visionsession;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Small ISO-BMFF fixture for structural validation; no decoder or AI dependency. */
public final class StreamingMp4TestSupport {
    private StreamingMp4TestSupport() {}
    public static byte[] mp4(int content) {
        return concat(box("ftyp", concat("isom".getBytes(StandardCharsets.US_ASCII), new byte[4], "mp42".getBytes(StandardCharsets.US_ASCII))),
                box("moov", box("trak", box("mdia", box("hdlr", concat(new byte[8], "vide".getBytes(StandardCharsets.US_ASCII)))))),
                box("mdat", new byte[]{(byte) content, 2, 3, 4}));
    }
    public static MediaFile file(int content) { return new MediaFile("recording.mp4", "video/mp4", mp4(content)); }
    public static byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(payload.length + 8).putInt(payload.length + 8).put(type.getBytes(StandardCharsets.US_ASCII)).put(payload).array();
    }
    public static byte[] concat(byte[]... parts) {
        int size = 0; for (var part : parts) size += part.length;
        var output = ByteBuffer.allocate(size); for (var part : parts) output.put(part); return output.array();
    }
}
