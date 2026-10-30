package com.omniguardy.backend.domain.securityevent.application.port.in;

import com.omniguardy.backend.domain.securityevent.application.model.SecurityEventResponse;
import com.omniguardy.backend.domain.securityevent.application.model.SecurityEventPageResponse;

public interface QuerySecurityEventsInputPort {
    default SecurityEventPageResponse list(Long userId, int page, int size) {
        return list(userId, page, size, null);
    }
    SecurityEventPageResponse list(Long userId, int page, int size, String riskLevel);
    SecurityEventResponse detail(Long userId, String eventId);
}
