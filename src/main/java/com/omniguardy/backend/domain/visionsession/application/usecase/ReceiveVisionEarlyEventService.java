package com.omniguardy.backend.domain.visionsession.application.usecase;

import com.omniguardy.backend.domain.visionsession.application.port.in.ReceiveVisionEarlyEventInputPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.*;
import com.omniguardy.backend.domain.visionsession.domain.error.DuplicateVisionEarlyEventException;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
import com.omniguardy.backend.global.performance.PerformanceLog;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionSessionErrorCode.*;
import static com.omniguardy.backend.domain.visionsession.domain.error.VisionEarlyEventErrorCode.*;

@Service
@RequiredArgsConstructor
public class ReceiveVisionEarlyEventService implements ReceiveVisionEarlyEventInputPort {
    private final VisionSessionRepository sessions;
    private final KeypadTriggerPort keypadTriggers;
    private final VisionAudioTriggerPort audioTriggers;
    private final VisionEarlyEventRepository earlyEvents;

    // No enclosing transaction: a duplicate insert rolls back before the replay read.
    @Override
    public void receive(VisionEarlyEvent event) {
        PerformanceLog.correlate(event.securityEventId(), event.triggerId(), event.sessionId(), event.triggerType().name());
        PerformanceLog.run("EARLY_EVENT_STORE", () -> receiveEvent(event));
    }

    private void receiveEvent(VisionEarlyEvent event) {
        var session = sessions.findBySessionId(event.sessionId())
                .orElseThrow(() -> new BusinessException(VISION_SESSION_NOT_FOUND));
        if (session.triggerType() != event.triggerType()) mismatch();
        if (session.triggerType() == VisionTriggerType.KEYPAD) {
            if (event.securityEventId() != null || !Objects.equals(session.triggerId(), event.triggerId())) mismatch();
            if (!keypadTriggers.exists(session.triggerId())) throw new BusinessException(VISION_TRIGGER_NOT_FOUND);
        } else {
            if (!Objects.equals(session.eventId(), event.securityEventId())) mismatch();
            var triggerId = audioTriggers.findIssuedTriggerId(session.eventId())
                    .orElseThrow(() -> new BusinessException(VISION_TRIGGER_NOT_FOUND));
            if (!triggerId.equals(event.triggerId())) mismatch();
        }
        try {
            earlyEvents.insert(event);
            PerformanceLog.result("STORED");
        } catch (DuplicateVisionEarlyEventException exception) {
            var stored = earlyEvents.find(event.sessionId(), event.eventInstanceId())
                    .orElseThrow(() -> new BusinessException(VISION_EARLY_EVENT_CONFLICT));
            if (!stored.equals(event)) throw new BusinessException(VISION_EARLY_EVENT_CONFLICT);
            PerformanceLog.result("REPLAY");
        }
    }

    private static void mismatch() { throw new BusinessException(VISION_CALLBACK_BINDING_MISMATCH); }
}
