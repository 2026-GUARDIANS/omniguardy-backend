package com.omniguardy.backend.domain.visionsession.application.port.out;

import java.util.Optional;

/** Read-only access to an issued AUDIO camera trigger; no SecurityEvent mutation. */
public interface VisionAudioTriggerPort {
    Optional<String> findIssuedTriggerId(String securityEventId);
}
