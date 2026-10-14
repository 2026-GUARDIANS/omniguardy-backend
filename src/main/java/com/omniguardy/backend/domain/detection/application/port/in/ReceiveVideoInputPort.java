package com.omniguardy.backend.domain.detection.application.port.in;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.model.VideoReceipt;
import com.omniguardy.backend.domain.detection.application.model.VideoTriggerContext;

public interface ReceiveVideoInputPort {

    VideoReceipt receive(
            MediaFile file,
            VideoTriggerContext triggerContext
    );
}
