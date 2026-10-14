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
        new TriggerKeypadCameraService(camera).trigger();
        var captor = ArgumentCaptor.forClass(CameraStartCommand.class);
        verify(camera).startCamera(captor.capture());
        assertEquals(TriggerType.KEYPAD, captor.getValue().triggerType());
        assertNotNull(captor.getValue().triggerId());
        assertNotNull(captor.getValue().triggeredAt());
        assertNull(captor.getValue().securityEventId());
    }
}
