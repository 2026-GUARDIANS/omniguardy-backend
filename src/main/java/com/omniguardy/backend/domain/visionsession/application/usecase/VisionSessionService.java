package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.securityevent.domain.model.EventTriggerType;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.visionsession.application.port.in.VisionSessionInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.KeypadTriggerPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.VisionSessionRepository;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionSessionException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.omniguardy.backend.global.performance.PerformanceLog;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;

@Service
@RequiredArgsConstructor
public class VisionSessionService implements VisionSessionInputPort {
    private final VisionSessionRepository sessions;
    private final KeypadTriggerPort keypadTriggers;
    private final SecurityEventRepository securityEvents;

    // Deliberately outside a transaction: a duplicate insert rolls back before the replay read.
    @Override
    public Registration register(String sessionId, VisionTriggerType type, String eventId, String triggerId) {
        PerformanceLog.correlate(eventId, triggerId, sessionId, type == null ? null : type.name());
        return PerformanceLog.measure("VISION_SESSION_REGISTER", () -> {
            var result = registerSession(sessionId, type, eventId, triggerId);
            PerformanceLog.result(result.created() ? "CREATED" : "REPLAY");
            return result;
        });
    }

    private Registration registerSession(String sessionId, VisionTriggerType type, String eventId, String triggerId) {
        var candidate = VisionSession.register(sessionId, type, eventId, triggerId);
        var existing = sessions.findBySessionId(sessionId);
        if (existing.isPresent()) return replay(existing.get(), candidate);
        validateTrigger(candidate);
        try {
            sessions.insert(candidate);
            return new Registration(candidate, true);
        } catch (DuplicateVisionSessionException exception) {
            return sessions.findBySessionId(sessionId).map(session -> replay(session, candidate))
                    .orElseThrow(() -> new BusinessException(VISION_SESSION_CONFLICT));
        }
    }

    private Registration replay(VisionSession existing, VisionSession candidate) {
        if (!existing.hasSameBinding(candidate)) throw new BusinessException(VISION_SESSION_CONFLICT);
        return new Registration(existing, false);
    }

    private void validateTrigger(VisionSession session) {
        if (session.triggerType() == VisionTriggerType.KEYPAD) {
            if (!keypadTriggers.exists(session.triggerId())) throw new BusinessException(VISION_TRIGGER_NOT_FOUND);
            return;
        }
        var event = securityEvents.findByEventId(session.eventId())
                .orElseThrow(() -> new BusinessException(VISION_TRIGGER_NOT_FOUND));
        if (!session.eventId().equals(event.getEventId())
                || event.getTriggerType() != EventTriggerType.AUDIO || event.getTriggeredAt() == null
                || !VisionSession.validId(event.getTriggerId(), 64)
                || !"CAMERA_REQUESTED".equals(event.getStatus())) {
            throw new BusinessException(INVALID_VISION_TRIGGER);
        }
    }

    @Override
    public VisionSession find(String sessionId) {
        if (!VisionSession.validId(sessionId, 128)) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        return sessions.findBySessionId(sessionId).orElseThrow(() -> new BusinessException(VISION_SESSION_NOT_FOUND));
    }
}
