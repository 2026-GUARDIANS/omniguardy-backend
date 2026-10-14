package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SecurityEventRepositoryAdapter implements SecurityEventRepository {
    private final SpringDataSecurityEventRepository repository;
    @Override public Optional<SecurityEvent> findByEventId(String eventId) { return repository.findByEventId(eventId); }
    @Override public SecurityEvent save(SecurityEvent event) { return repository.save(event); }
}

