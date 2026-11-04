package com.omniguardy.backend.domain.visionsession.application.port.in;

public interface LinkStreamingVideoInputPort {
    /** Conflict is explicit; temporary failures are retried by reconciliation. */
    void afterUpload(String sessionId);
    void afterEvent(String sessionId);
    void reconcile();
}
