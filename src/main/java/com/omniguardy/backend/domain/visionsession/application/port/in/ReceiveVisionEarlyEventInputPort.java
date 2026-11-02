package com.omniguardy.backend.domain.visionsession.application.port.in;

import com.omniguardy.backend.domain.visionsession.domain.model.VisionEarlyEvent;

public interface ReceiveVisionEarlyEventInputPort {
    void receive(VisionEarlyEvent event);
}
