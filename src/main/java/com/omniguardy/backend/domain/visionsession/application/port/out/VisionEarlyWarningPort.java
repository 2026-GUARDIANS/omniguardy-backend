package com.omniguardy.backend.domain.visionsession.application.port.out;

public interface VisionEarlyWarningPort {
    /** Called only after binding validation and immutable callback persistence (including replay). */
    void request(String sessionId);
}
