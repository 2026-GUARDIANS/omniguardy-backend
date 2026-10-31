package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryHomeSummaryUseCaseTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void returnsLatestRiskAndDetectionTimeAndCountsOnlyToday() {
        var repository = mock(SecurityEventRepository.class);
        var useCase = new QueryHomeSummaryUseCase(repository);
        ReflectionTestUtils.setField(useCase, "clock",
                Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), SEOUL));
        var detectedAt = OffsetDateTime.parse("2026-10-31T11:59:00+09:00");
        var latest = SecurityEvent.builder().eventId("latest").status("COMPLETED")
                .riskLevel("HIGH").triggeredAt(detectedAt).build();
        when(repository.findLatestByUserId(17L)).thenReturn(Optional.of(latest));
        when(repository.countByUserIdAndCreatedAtBetween(17L,
                LocalDateTime.of(2026, 10, 31, 0, 0),
                LocalDateTime.of(2026, 11, 1, 0, 0))).thenReturn(3L);

        var response = useCase.query(17L);

        assertEquals("HIGH", response.securityStatus());
        assertEquals(detectedAt, response.lastDetectedAt());
        assertEquals(3L, response.todayEventCount());
    }

    @Test
    void safelyReturnsNullStatusAndTimeWhenThereAreNoEvents() {
        var repository = mock(SecurityEventRepository.class);
        var useCase = new QueryHomeSummaryUseCase(repository);
        ReflectionTestUtils.setField(useCase, "clock",
                Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), SEOUL));
        when(repository.findLatestByUserId(17L)).thenReturn(Optional.empty());

        var response = useCase.query(17L);

        assertNull(response.securityStatus());
        assertNull(response.lastDetectedAt());
        assertEquals(0L, response.todayEventCount());
    }

    @Test
    void rejectsMissingAuthenticatedUser() {
        var repository = mock(SecurityEventRepository.class);
        var useCase = new QueryHomeSummaryUseCase(repository);

        assertThrows(BusinessException.class, () -> useCase.query(null));
        verifyNoInteractions(repository);
    }
}
