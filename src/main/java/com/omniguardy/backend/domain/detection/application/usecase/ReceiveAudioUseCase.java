package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioInputPort;
import com.omniguardy.backend.domain.detection.application.port.out.AudioAnalysisPort;
import com.omniguardy.backend.domain.detection.application.service.AudioDetectionService;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReceiveAudioUseCase implements ReceiveAudioInputPort {
    private final AudioAnalysisPort audioAnalysisPort;
    private final AudioDetectionService detectionService;

    public AudioReceipt receive(MediaFile file) {
        if (file == null || file.bytes().length == 0) throw new BusinessException(DetectionErrorCode.EMPTY_AUDIO_FILE);
        String original = file.originalFilename() == null || file.originalFilename().isBlank()
                ? "audio.wav" : file.originalFilename();
        AudioAnalysis analysis = audioAnalysisPort.analyze(file);
        var result = detectionService.process(analysis, file, null);
        return new AudioReceipt(result.eventId(), result.filename() == null ? original : result.filename(),
                file.size(), analysis.eventType(), analysis.probability());
    }
}
