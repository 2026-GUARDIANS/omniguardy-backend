package com.omniguardy.backend.domain.securityevent.domain.repository;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import java.util.Optional;

public interface SecurityEventRepository {
    Optional<SecurityEvent> findByEventId(String eventId);
    SecurityEvent save(SecurityEvent event);
}

