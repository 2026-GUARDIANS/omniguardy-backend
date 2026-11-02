package com.omniguardy.backend.domain.visionsession.application.port.out;
public interface VisionFallbackFailureClassifier {
    boolean permanent(Throwable failure);
    String reason(Throwable failure);
}

