package com.omniguardy.backend.domain.securityevent.application.port.in;

import com.omniguardy.backend.domain.securityevent.application.port.out.SecurityResponseLedger.Channel;

public interface DispatchEarlyWarningInputPort {
    void enqueue(String sessionId);
    void recover();
    void dispatch(Channel channel);
}
