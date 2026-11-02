package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.CameraStartCommand;
import com.omniguardy.backend.domain.detection.application.model.TriggerType;
import com.omniguardy.backend.domain.detection.application.port.in.TriggerKeypadCameraUseCase;
import com.omniguardy.backend.domain.detection.application.port.out.CameraCommandPort;
import com.omniguardy.backend.domain.visionsession.application.port.out.KeypadTriggerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TriggerKeypadCameraService
        implements TriggerKeypadCameraUseCase {

    private final CameraCommandPort cameraCommandPort;
    private final KeypadTriggerPort keypadTriggers;

    @Override
    @Transactional
    public void trigger() {

        CameraStartCommand command = new CameraStartCommand(
                "trg-" + UUID.randomUUID(),
                TriggerType.KEYPAD,
                null,
                OffsetDateTime.now(),
                10
        );

        keypadTriggers.record(command.triggerId(), command.triggeredAt());
        cameraCommandPort.startCamera(command);
    }
}
