package com.omniguardy.backend.domain.securityevent.application.usecase;

import com.omniguardy.backend.domain.securityevent.application.port.in.LinkSecurityEventVideoInputPort;
import com.omniguardy.backend.domain.securityevent.domain.model.VideoLinkResult;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LinkSecurityEventVideoService implements LinkSecurityEventVideoInputPort {
    private final SecurityEventRepository events;

    @Override public VideoLinkResult link(Command command) {
        if (command.eventId() == null || command.triggerType() == null || command.triggerId() == null
                || command.storagePath() == null || command.storagePath().isBlank() || command.storagePath().length() > 500)
            return VideoLinkResult.INVALID_BINDING;
        return events.linkVideo(command.eventId(), command.triggerType(), command.triggerId(), command.storagePath());
    }
}
