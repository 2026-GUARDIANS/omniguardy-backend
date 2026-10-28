package com.omniguardy.backend.domain.tts.infrastructure.delivery;

import com.omniguardy.backend.domain.tts.application.port.out.TtsDeliveryPort;
import com.omniguardy.backend.domain.tts.domain.error.TtsErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.tts.enabled", havingValue = "false")
public class DisabledTtsAdapter implements TtsDeliveryPort {
    @Override public void synthesizeAndPublish(String message) {
        throw new BusinessException(TtsErrorCode.DISABLED);
    }
}
