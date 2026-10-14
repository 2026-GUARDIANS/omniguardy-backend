package com.omniguardy.backend.domain.securityevent.application.port.in;

import java.time.OffsetDateTime;

public interface RecordKeypadVisionInputPort {
    String record(Command command);

    record Command(String triggerId, OffsetDateTime triggeredAt, String videoPath,
                   String prediction, Double confidence, String classProbabilities,
                   String visionEvents, Integer personCount, Double videoDurationSeconds,
                   OffsetDateTime analyzedAt) {}
}
