package com.omniguardy.backend.domain.detection.application.port.in;

import com.omniguardy.backend.domain.detection.application.model.AudioReceipt;
import com.omniguardy.backend.domain.detection.application.model.MediaFile;

public interface ReceiveAudioInputPort {
    AudioReceipt receive(MediaFile file);
}
