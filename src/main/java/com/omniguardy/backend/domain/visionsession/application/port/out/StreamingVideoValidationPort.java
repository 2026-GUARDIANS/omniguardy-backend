package com.omniguardy.backend.domain.visionsession.application.port.out;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.application.model.StreamingVideoFileInfo;

public interface StreamingVideoValidationPort {
    StreamingVideoFileInfo validate(MediaFile file);
}
