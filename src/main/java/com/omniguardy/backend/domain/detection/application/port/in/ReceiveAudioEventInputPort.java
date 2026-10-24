package com.omniguardy.backend.domain.detection.application.port.in;

import com.omniguardy.backend.domain.detection.application.model.AudioAnalysis;
import com.omniguardy.backend.domain.detection.application.model.AudioDetectionResult;

public interface ReceiveAudioEventInputPort {
    AudioDetectionResult receive(String detectionId, AudioAnalysis analysis);
}
