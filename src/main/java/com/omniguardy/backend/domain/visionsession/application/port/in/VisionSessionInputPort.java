package com.omniguardy.backend.domain.visionsession.application.port.in;

import com.omniguardy.backend.domain.visionsession.domain.model.VisionSession;
import com.omniguardy.backend.domain.visionsession.domain.model.VisionTriggerType;

public interface VisionSessionInputPort {
    Registration register(String sessionId, VisionTriggerType triggerType, String eventId, String triggerId);
    VisionSession find(String sessionId);
    record Registration(VisionSession session, boolean created) {}
}
