package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.visionsession.domain.model.VisionSession;
import java.util.Optional;

public interface VisionSessionRepository {
    Optional<VisionSession> findBySessionId(String sessionId);
    void insert(VisionSession session);
}
