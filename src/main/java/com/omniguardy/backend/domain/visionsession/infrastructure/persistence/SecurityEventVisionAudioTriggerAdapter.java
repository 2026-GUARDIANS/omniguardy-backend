package com.omniguardy.backend.domain.visionsession.infrastructure.persistence;

import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.application.port.out.VisionAudioTriggerPort;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SecurityEventVisionAudioTriggerAdapter implements VisionAudioTriggerPort {
    private final SecurityEventRepository events;

    @Override
    public Optional<String> findIssuedTriggerId(String securityEventId) {
        return events.findByEventId(securityEventId)
                .filter(event -> securityEventId.equals(event.getEventId())
                        && event.getTriggerType() == EventTriggerType.AUDIO && event.getTriggeredAt() != null
                        && VisionSession.validId(event.getTriggerId(), 64))
                .map(event -> event.getTriggerId());
    }
}
