package com.omniguardy.backend.domain.visionsession.domain.error;

/** Sanitized upstream failure; response bodies and credentials are not propagated into logs. */
public class StreamingVisionException extends RuntimeException {
    private final String code;
    private final boolean retryable;
    public StreamingVisionException(String code, boolean retryable) {
        super(code); this.code = code; this.retryable = retryable;
    }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
