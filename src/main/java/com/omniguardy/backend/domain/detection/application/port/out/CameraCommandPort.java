package com.omniguardy.backend.domain.detection.application.port.out;

import com.omniguardy.backend.domain.detection.application.model.CameraStartCommand;

public interface CameraCommandPort {

    void startCamera(CameraStartCommand command);
}
