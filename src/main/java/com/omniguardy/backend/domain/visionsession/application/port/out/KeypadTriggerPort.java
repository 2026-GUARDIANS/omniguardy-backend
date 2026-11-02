package com.omniguardy.backend.domain.visionsession.application.port.out;

import java.time.OffsetDateTime;

/** Records issued keypad commands without creating SecurityEvents. */
public interface KeypadTriggerPort {
    void record(String triggerId, OffsetDateTime triggeredAt);
    boolean exists(String triggerId);
}
