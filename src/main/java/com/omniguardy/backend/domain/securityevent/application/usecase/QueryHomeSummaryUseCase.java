package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.model.HomeSummaryResponse;
import com.omniguardy.backend.domain.securityevent.application.port.in.QueryHomeSummaryInputPort;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueryHomeSummaryUseCase implements QueryHomeSummaryInputPort {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final SecurityEventRepository repository;
    private Clock clock = Clock.system(SERVICE_ZONE);

    @Override
    public HomeSummaryResponse query(Long userId) {
        if (userId == null) {
            throw new BusinessException(GlobalErrorCode.AUTHENTICATION_REQUIRED);
        }

        LocalDate today = LocalDate.now(clock);
        var start = today.atStartOfDay();
        var end = today.plusDays(1).atStartOfDay();
        var latest = repository.findLatestByUserId(userId);
        long todayEventCount = repository.countByUserIdAndCreatedAtBetween(userId, start, end);

        return new HomeSummaryResponse(
                latest.map(SecurityEvent::getRiskLevel).orElse(null),
                latest.map(this::detectedAt).orElse(null),
                todayEventCount);
    }

    private OffsetDateTime detectedAt(SecurityEvent event) {
        if (event.getTriggeredAt() != null) {
            return event.getTriggeredAt();
        }
        return event.getCreatedAt() == null
                ? null
                : event.getCreatedAt().atZone(SERVICE_ZONE).toOffsetDateTime();
    }
}
