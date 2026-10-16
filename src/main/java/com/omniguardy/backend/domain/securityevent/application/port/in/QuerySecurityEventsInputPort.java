package com.omniguardy.backend.domain.securityevent.application.port.in;

import com.omniguardy.backend.domain.securityevent.application.model.SecurityEventResponse;
import com.omniguardy.backend.domain.securityevent.application.model.SecurityEventPageResponse;

public interface QuerySecurityEventsInputPort {
    SecurityEventPageResponse list(Long userId, int page, int size);
    SecurityEventResponse detail(Long userId, String eventId);
}
