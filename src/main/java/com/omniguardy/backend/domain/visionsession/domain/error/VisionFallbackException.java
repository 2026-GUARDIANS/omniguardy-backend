package com.omniguardy.backend.domain.visionsession.domain.error;
public class VisionFallbackException extends RuntimeException {
    private final boolean permanent;
    public VisionFallbackException(String reason,boolean permanent) { super(reason); this.permanent=permanent; }
    public boolean permanent() { return permanent; }
}

