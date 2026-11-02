package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.CameraStartCommand;
import com.omniguardy.backend.domain.detection.application.model.TriggerType;
import com.omniguardy.backend.domain.detection.application.port.out.CameraCommandPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TriggerKeypadCameraServiceTest {
    @Test void firstInputOnlyRequestsCameraWithoutSecurityEventId() {
        CameraCommandPort camera = mock(CameraCommandPort.class);
        var triggers = mock(com.omniguardy.backend.domain.visionsession.application.port.out.KeypadTriggerPort.class);
        new TriggerKeypadCameraService(camera, triggers).trigger();
        var captor = ArgumentCaptor.forClass(CameraStartCommand.class);
        verify(camera).startCamera(captor.capture());
        assertEquals(TriggerType.KEYPAD, captor.getValue().triggerType());
        assertNotNull(captor.getValue().triggerId());
        assertNotNull(captor.getValue().triggeredAt());
        assertNull(captor.getValue().securityEventId());
        var ordered = inOrder(triggers, camera);
        ordered.verify(triggers).record(captor.getValue().triggerId(), captor.getValue().triggeredAt());
        ordered.verify(camera).startCamera(captor.getValue());
    }
}
