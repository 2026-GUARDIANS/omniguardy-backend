package com.omniguardy.backend.domain.securityevent.application.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record HomeSummaryResponse(
        @Schema(description = "가장 최근 보안 이벤트의 위험도", example = "HIGH", nullable = true,
                allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
        String securityStatus,
        @Schema(description = "마지막 감지 시각", example = "2026-10-31T14:20:00+09:00", nullable = true)
        OffsetDateTime lastDetectedAt,
        @Schema(description = "오늘 발생한 보안 이벤트 수", example = "3")
        long todayEventCount
) {
}
