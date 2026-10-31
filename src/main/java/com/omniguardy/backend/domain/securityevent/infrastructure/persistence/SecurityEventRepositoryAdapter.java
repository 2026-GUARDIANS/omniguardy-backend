package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
@RequiredArgsConstructor
public class SecurityEventRepositoryAdapter implements SecurityEventRepository {
    private final SpringDataSecurityEventRepository repository;
    @Override public Optional<SecurityEvent> findByEventId(String eventId) { return repository.findByEventId(eventId); }
    @Override public Page<SecurityEvent> findAllByUserId(Long userId, Pageable pageable) { return repository.findAllByUser_Id(userId, pageable); }
    @Override public Page<SecurityEvent> findAllByUserIdAndRiskLevel(Long userId, String riskLevel, Pageable pageable) {
        return repository.findAllByUser_IdAndRiskLevel(userId, riskLevel, pageable);
    }
    @Override public Optional<SecurityEvent> findByEventIdAndUserId(String eventId, Long userId) { return repository.findByEventIdAndUser_Id(eventId, userId); }
    @Override public Optional<SecurityEvent> findLatestByUserId(Long userId) {
        return repository.findFirstByUser_IdOrderByCreatedAtDescIdDesc(userId);
    }
    @Override public long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end) {
        return repository.countByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(userId, start, end);
    }
    @Override public SecurityEvent save(SecurityEvent event) { return repository.save(event); }
}

