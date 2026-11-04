package com.omniguardy.backend.domain.securityevent.infrastructure.response;

import com.omniguardy.backend.domain.securityevent.application.port.out.*;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.notification.application.usecase.NotificationService;
import com.omniguardy.backend.domain.sound.application.port.out.SoundCommandPort;
import com.omniguardy.backend.domain.sound.domain.model.SoundType;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.error.UserErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;

@Component @RequiredArgsConstructor
public class ExistingEarlyWarningDeliveryAdapter implements EarlyWarningDeliveryPort {
    private final SecurityEventRepository events;
    private final DemoUserResolver keypadOwner;
    private final NotificationService notifications;
    private final SoundCommandPort sound;

    @Override @Transactional
    public String sendPush(SecurityResponseLedger.Binding b) {
        var owner = b.eventId() == null ? resolveKeypadOwner()
                : events.findByEventId(b.eventId()).map(event -> {
                    if (!b.triggerId().equals(event.getTriggerId()) || !b.triggerType().equals(event.getTriggerType().name()))
                        throw new IllegalStateException("Early warning event binding mismatch");
                    return event.getUser();
                }).orElse(null);
        if (owner == null) return "NO_RECIPIENT";
        return notifications.sendEarlySecurityAlert(owner, b.eventId(), b.triggerType())
                ? "REQUEST_ACCEPTED" : "NO_ACTIVE_TOKENS";
    }

    private User resolveKeypadOwner() {
        try {
            return keypadOwner.resolve().orElse(null);
        } catch (BusinessException failure) {
            // These failures happen before any provider request. Preserve the final response opportunity.
            if (failure.getErrorCode() == UserErrorCode.DEMO_USER_ID_NOT_CONFIGURED
                    || failure.getErrorCode() == UserErrorCode.USER_NOT_FOUND) return null;
            throw failure;
        }
    }

    @Override public void sendSiren() { sound.send(SoundType.SIREN); }
}
