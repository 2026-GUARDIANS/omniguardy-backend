package com.omniguardy.backend.domain.detection.application.service;

import com.omniguardy.backend.domain.detection.application.model.*;
import com.omniguardy.backend.domain.detection.application.port.out.*;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.domain.user.application.usecase.DemoUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.omniguardy.backend.domain.detection.application.model.AudioDetectionResult.Outcome.*;

@Service
@RequiredArgsConstructor
public class AudioDetectionService {
    private static final long CAMERA_COOLDOWN_MS = 12_000L;
    private final MediaStoragePort mediaStoragePort;
    private final CameraCommandPort cameraCommandPort;
    private final SecurityEventRepository securityEventRepository;
    private final DemoUserResolver demoUserResolver;
    private final AudioDetectionReceiptPort receiptPort;
    private long lastCameraTriggeredAt;
    private Clock clock = Clock.systemUTC();

    @Transactional
    public AudioDetectionResult process(AudioAnalysis analysis, MediaFile file, String detectionId) {
        if (detectionId != null) receiptPort.claim(detectionId);
        if (analysis.eventType() == AudioEventType.BACKGROUND) {
            return new AudioDetectionResult(null, BACKGROUND_IGNORED, null);
        }
        if (isCoolingDown()) return new AudioDetectionResult(null, COOLDOWN_SUPPRESSED, null);

        var user = demoUserResolver.resolve().orElse(null);
        String eventId = UUID.randomUUID().toString();
        OffsetDateTime triggeredAt = OffsetDateTime.now();
        MediaStoragePort.StoredMedia stored = file == null ? null : mediaStoragePort.saveAudio(eventId, file);
        SecurityEvent event = SecurityEvent.builder().eventId(eventId).user(user)
                .audioEventType(analysis.eventType()).audioProbability(analysis.probability())
                .audioPath(stored == null ? null : stored.path()).status("AUDIO_DETECTED").build();
        event.connectAudioTrigger(eventId, triggeredAt);
        securityEventRepository.save(event);
        cameraCommandPort.startCamera(new CameraStartCommand(eventId, TriggerType.AUDIO,
                eventId, triggeredAt, 10));
        event.markCameraRequested();
        return new AudioDetectionResult(eventId, TRIGGERED, stored == null ? null : stored.filename());
    }

    private synchronized boolean isCoolingDown() {
        long now = clock.millis();
        if (now - lastCameraTriggeredAt < CAMERA_COOLDOWN_MS) return true;
        lastCameraTriggeredAt = now;
        return false;
    }
}
