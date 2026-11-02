package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.visionsession.domain.model.VisionEarlyEvent;
import java.util.Optional;

public interface VisionEarlyEventRepository {
    void insert(VisionEarlyEvent event);
    Optional<VisionEarlyEvent> find(String sessionId, String eventInstanceId);
}
