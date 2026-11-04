package com.omniguardy.backend.domain.visionsession.infrastructure.video;

import com.omniguardy.backend.domain.securityevent.application.port.in.LinkSecurityEventVideoInputPort;
import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class SecurityEventStreamingVideoLinkAdapter implements StreamingVideoEventLinkPort {
    private final LinkSecurityEventVideoInputPort events;
    @Override public Result link(StreamingVideoLinkRepository.Candidate candidate) {
        return Result.valueOf(events.link(new LinkSecurityEventVideoInputPort.Command(candidate.eventId(),
                EventTriggerType.valueOf(candidate.triggerType().name()), candidate.triggerId(), candidate.storagePath())).name());
    }
}
