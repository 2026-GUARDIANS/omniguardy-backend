package com.omniguardy.backend.domain.securityevent.application.port.in;

import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.model.VideoLinkResult;

public interface LinkSecurityEventVideoInputPort {
    record Command(String eventId, EventTriggerType triggerType, String triggerId, String storagePath) {}
    VideoLinkResult link(Command command);
}
