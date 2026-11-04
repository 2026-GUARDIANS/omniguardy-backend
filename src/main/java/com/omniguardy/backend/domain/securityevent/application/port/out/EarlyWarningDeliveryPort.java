package com.omniguardy.backend.domain.securityevent.application.port.out;

public interface EarlyWarningDeliveryPort {
    /** Accepted means provider/client handoff, never confirmed recipient delivery. */
    String sendPush(SecurityResponseLedger.Binding binding);
    void sendSiren();
}
