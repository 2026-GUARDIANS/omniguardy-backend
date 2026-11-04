package com.omniguardy.backend.domain.securityevent.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** Read-only observation of existing server timestamps; never consult client capture/emission clocks. */
public interface ServerEventTimingPort {
    record Timing(String sessionId, Instant observedAt, String baseline) {}
    Optional<Timing> find(String eventId);
}
