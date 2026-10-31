package com.omniguardy.backend.domain.securityevent.domain.repository;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SecurityEventRepository {
    Optional<SecurityEvent> findByEventId(String eventId);
    Page<SecurityEvent> findAllByUserId(Long userId, Pageable pageable);
    Page<SecurityEvent> findAllByUserIdAndRiskLevel(Long userId, String riskLevel, Pageable pageable);
    Optional<SecurityEvent> findByEventIdAndUserId(String eventId, Long userId);
    Optional<SecurityEvent> findLatestByUserId(Long userId);
    long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
    SecurityEvent save(SecurityEvent event);
}

