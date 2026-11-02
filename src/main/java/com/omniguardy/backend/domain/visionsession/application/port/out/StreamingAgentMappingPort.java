package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;
import com.omniguardy.backend.domain.visionsession.domain.model.StreamingAgentSource;

public interface StreamingAgentMappingPort {
    VisionAnalysis map(StreamingAgentSource source);
}
