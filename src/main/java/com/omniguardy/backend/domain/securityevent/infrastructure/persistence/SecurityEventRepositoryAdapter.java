package com.omniguardy.backend.domain.securityevent.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.model.VideoLinkResult;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Objects;

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
    @Override @Transactional
    public SecurityEvent save(SecurityEvent event) {
        if (event.getId() != null) {
            var rows = repository.lockVideoBinding(event.getEventId());
            if (!rows.isEmpty() && event.getVideoPath() == null && rows.getFirst()[4] != null)
                event.updateVideoPath((String) rows.getFirst()[4]);
        }
        // The lock lasts through merge/flush/commit, serializing this save with linkVideo.
        return repository.save(event);
    }

    @Override @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VideoLinkResult linkVideo(String eventId, EventTriggerType type, String triggerId, String path) {
        var rows = repository.lockVideoBinding(eventId);
        if (rows.isEmpty()) return VideoLinkResult.NOT_READY;
        var row = rows.getFirst();
        if (!eventId.equals(row[1]) || !type.name().equals(row[2]) || !Objects.equals(triggerId, row[3]))
            return VideoLinkResult.INVALID_BINDING;
        if (row[4] != null) return path.equals(row[4]) ? VideoLinkResult.ALREADY_LINKED : VideoLinkResult.CONFLICT;
        return repository.attachVideoPath(((Number) row[0]).longValue(), path) == 1
                ? VideoLinkResult.LINKED : VideoLinkResult.CONFLICT;
    }
}

