package com.omniguardy.backend.domain.tts.application.usecase;

import com.omniguardy.backend.domain.tts.application.model.PublishTtsCommand;
import com.omniguardy.backend.domain.tts.application.port.out.TtsDeliveryPort;
import com.omniguardy.backend.domain.tts.domain.error.TtsErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublishTtsUseCase {
    private final TtsDeliveryPort deliveryPort;

    public void publish(PublishTtsCommand command) {
        if (command.message().length() > 100) throw new BusinessException(TtsErrorCode.MESSAGE_TOO_LONG);
        deliveryPort.synthesizeAndPublish(command.message());
    }
}
