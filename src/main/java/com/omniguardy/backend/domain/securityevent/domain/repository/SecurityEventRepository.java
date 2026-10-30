package com.omniguardy.backend.domain.securityevent.domain.repository;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SecurityEventRepository {
    Optional<SecurityEvent> findByEventId(String eventId);
    Page<SecurityEvent> findAllByUserId(Long userId, Pageable pageable);
    Page<SecurityEvent> findAllByUserIdAndRiskLevel(Long userId, String riskLevel, Pageable pageable);
    Optional<SecurityEvent> findByEventIdAndUserId(String eventId, Long userId);
    SecurityEvent save(SecurityEvent event);
}

