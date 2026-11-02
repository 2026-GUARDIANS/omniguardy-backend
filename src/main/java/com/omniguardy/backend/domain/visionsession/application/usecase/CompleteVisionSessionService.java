package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.in.CompleteVisionSessionInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;

@Service
@RequiredArgsConstructor
public class CompleteVisionSessionService implements CompleteVisionSessionInputPort {
    private final VisionSessionRepository sessions;
    private final VisionPollingRepository jobs;
    private final KeypadTriggerPort keypadTriggers;
    private final VisionAudioTriggerPort audioTriggers;
    private final VisionPollingPolicy policy;

    @Override
    public VisionProcessingView complete(String sessionId) {
        if (!VisionSession.validId(sessionId, 128)) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        var session = sessions.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(VISION_SESSION_NOT_FOUND));
        var existing = jobs.find(sessionId);
        if (existing.isPresent()) return VisionProcessingView.from(existing.get());
        if (session.status() != VisionSessionStatus.REGISTERED) throw new BusinessException(INVALID_VISION_SESSION_STATUS);
        String triggerId;
        if (session.triggerType() == VisionTriggerType.KEYPAD) {
            if (!keypadTriggers.exists(session.triggerId())) throw new BusinessException(VISION_TRIGGER_NOT_FOUND);
            triggerId = session.triggerId();
        } else {
            triggerId = audioTriggers.findIssuedTriggerId(session.eventId())
                    .orElseThrow(() -> new BusinessException(VISION_TRIGGER_NOT_FOUND));
        }
        return VisionProcessingView.from(jobs.enqueue(new VisionBinding(sessionId, session.triggerType(), triggerId,
                session.eventId()), policy.maxWait()));
    }
}
