package com.omniguardy.backend.domain.visionsession.infrastructure.earlywarning;

import com.omniguardy.backend.domain.visionsession.application.port.out.VisionEarlyWarningPort;
import com.omniguardy.backend.domain.securityevent.application.port.in.DispatchEarlyWarningInputPort;
import com.omniguardy.backend.domain.securityevent.infrastructure.response.EarlyWarningWorkers.EarlyWarningKick;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ExistingVisionEarlyWarningAdapter implements VisionEarlyWarningPort {
    private final DispatchEarlyWarningInputPort warnings;
    private final EarlyWarningKick kick;
    @Override public void request(String sessionId) {
        warnings.enqueue(sessionId);
        kick.request();
    }
}
