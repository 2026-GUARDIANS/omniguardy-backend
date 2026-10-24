package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.AudioAnalysis;
import com.omniguardy.backend.domain.detection.application.model.AudioDetectionResult;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioEventInputPort;
import com.omniguardy.backend.domain.detection.application.service.AudioDetectionService;
import com.omniguardy.backend.domain.detection.domain.error.DuplicateAudioDetectionException;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReceiveAudioEventUseCase implements ReceiveAudioEventInputPort {
    private final AudioDetectionService detectionService;

    @Override
    public AudioDetectionResult receive(String detectionId, AudioAnalysis analysis) {
        if (detectionId == null || !detectionId.matches("[A-Za-z0-9_-]{1,128}") || analysis == null
                || analysis.eventType() == null || !Double.isFinite(analysis.probability())
                || analysis.probability() < 0 || analysis.probability() > 1) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        // Catch outside the transactional service: a duplicate insert rolls back its transaction.
        try {
            return detectionService.process(analysis, null, detectionId);
        } catch (DuplicateAudioDetectionException exception) {
            return new AudioDetectionResult(null, AudioDetectionResult.Outcome.DUPLICATE, null);
        }
    }
}
