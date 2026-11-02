package com.omniguardy.backend.domain.visionsession.application.port.out;

public interface StreamingAgentFailureClassifier {
    enum Kind { RETRYABLE, PERMANENT, UNKNOWN }
    Kind classify(Throwable failure);
    String code(Throwable failure);
}
