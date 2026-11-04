package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.QueryHint;

interface SpringDataSecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    // Do not flush a stale managed entity before reading the durable path under the row lock.
    @Query(value = "SELECT id, event_id, trigger_type, trigger_id, video_path FROM security_events WHERE event_id = :eventId FOR UPDATE", nativeQuery = true)
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    List<Object[]> lockVideoBinding(String eventId);

    @Modifying
    @Query(value = "UPDATE security_events SET video_path = :path, updated_at = CURRENT_TIMESTAMP WHERE id = :id AND video_path IS NULL", nativeQuery = true)
    int attachVideoPath(Long id, String path);

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
