package com.omniguardy.backend.domain.securityevent.application.port.in;

import com.omniguardy.backend.domain.securityevent.application.model.HomeSummaryResponse;

public interface QueryHomeSummaryInputPort {
    HomeSummaryResponse query(Long userId);
}
