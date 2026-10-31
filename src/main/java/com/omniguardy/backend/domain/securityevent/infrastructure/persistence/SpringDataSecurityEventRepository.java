package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.time.LocalDateTime;

interface SpringDataSecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<SecurityEvent> findByEventId(String eventId);
    @EntityGraph(attributePaths = "user")
    Page<SecurityEvent> findAllByUser_Id(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<SecurityEvent> findAllByUser_IdAndRiskLevel(Long userId, String riskLevel, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<SecurityEvent> findByEventIdAndUser_Id(String eventId, Long userId);

    @EntityGraph(attributePaths = "user")
    Optional<SecurityEvent> findFirstByUser_IdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, LocalDateTime start, LocalDateTime end);
}
