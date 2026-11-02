package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.visionsession.domain.model.*;
import java.util.Optional;

public interface StreamingVisionQueryPort {
    StreamingVisionStatus status(String sessionId);
    /** Empty means HTTP 202, which is a pending response, not a FINAL_RESULT. */
    Optional<StreamingVisionFinalResult> result(String sessionId);
}
