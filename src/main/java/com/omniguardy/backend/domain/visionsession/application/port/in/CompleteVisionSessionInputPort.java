package com.omniguardy.backend.domain.visionsession.application.port.in;

import com.omniguardy.backend.domain.visionsession.application.model.VisionProcessingView;

public interface CompleteVisionSessionInputPort {
    VisionProcessingView complete(String sessionId);
}
