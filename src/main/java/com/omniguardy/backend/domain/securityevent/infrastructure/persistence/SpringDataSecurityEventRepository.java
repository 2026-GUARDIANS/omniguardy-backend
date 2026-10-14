package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataSecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    Optional<SecurityEvent> findByEventId(String eventId);
}

