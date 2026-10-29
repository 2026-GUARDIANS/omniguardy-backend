package com.omniguardy.backend.domain.detection.application.model;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** File-backed media belongs to the request; the controller cleans up its temporary file. */
public final class MediaFile {
    private final String originalFilename;
    private final String contentType;
    private final byte[] data;
    private final Path path;
    public MediaFile(String name, String type, byte[] bytes) {
        this(name, type, bytes == null ? new byte[0] : bytes.clone(), null);
    }
    private MediaFile(String name, String type, byte[] bytes, Path path) {
        this.originalFilename = name; this.contentType = type; this.data = bytes; this.path = path;
    }
    public static MediaFile fromPath(String name, String type, Path path) {
        return new MediaFile(name, type, null, path);
    }
    public String originalFilename() { return originalFilename; }
    public String contentType() { return contentType; }
    public InputStream openStream() throws IOException {
        return path == null ? new ByteArrayInputStream(data) : Files.newInputStream(path);
    }
    public long size() {
        try { return path == null ? data.length : Files.size(path); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public byte[] bytes() {
        try { return path == null ? data.clone() : Files.readAllBytes(path); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public String sha256() {
        try (InputStream in = openStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) != -1;) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) { throw new UncheckedIOException(e); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
