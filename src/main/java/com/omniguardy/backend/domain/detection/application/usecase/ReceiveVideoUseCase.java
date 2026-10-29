package com.omniguardy.backend.domain.detection.application.usecase;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.detection.application.model.VideoReceipt;
import com.omniguardy.backend.domain.detection.application.model.VideoTriggerContext;
import com.omniguardy.backend.domain.detection.application.model.VisionAnalysis;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveVideoInputPort;
import com.omniguardy.backend.domain.detection.application.port.out.MediaStoragePort;
import com.omniguardy.backend.domain.detection.application.port.out.VideoUploadJournal;
import com.omniguardy.backend.domain.detection.application.port.out.VisionAnalysisPort;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.domain.securityevent.application.usecase.AnalyzeAgentRiskUseCase;
import com.omniguardy.backend.domain.securityevent.domain.error.SecurityEventErrorCode;
import com.omniguardy.backend.domain.securityevent.domain.model.SecurityEvent;
import com.omniguardy.backend.domain.securityevent.domain.repository.SecurityEventRepository;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import java.time.OffsetDateTime;
import com.omniguardy.backend.domain.detection.application.model.TriggerType;
import com.omniguardy.backend.domain.securityevent.application.port.in.RecordKeypadVisionInputPort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ReceiveVideoUseCase implements ReceiveVideoInputPort {

    private static final long MAX_VIDEO_SIZE =
            100L * 1024 * 1024;

    private final VisionAnalysisPort visionAnalysisPort;
    private final MediaStoragePort mediaStoragePort;

    // 기존 AUDIO 흐름 유지
    private final SecurityEventRepository securityEventRepository;
    private final AnalyzeAgentRiskUseCase analyzeAgentRiskUseCase;

    private final ObjectMapper objectMapper;
    private final RecordKeypadVisionInputPort recordKeypadVisionInputPort;

    private VideoUploadJournal journal;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setVideoUploadJournal(VideoUploadJournal journal) {
        this.journal = journal;
    }

    @Override
    public VideoReceipt receive(
            MediaFile file,
            VideoTriggerContext triggerContext
    ) {

        validateFile(file);

        if (triggerContext == null
                || triggerContext.triggerType() == null) {

            throw new IllegalArgumentException(
                    "triggerType is required"
            );
        }

        if (triggerContext.triggerType() == TriggerType.KEYPAD) validateRequiredTriggerFields(triggerContext);
        var upload = journal == null ? null : journal.begin(file, triggerContext);
        try {
            if (upload != null && upload.completed() != null) return upload.completed();
            VideoReceipt result = switch (triggerContext.triggerType()) {
                case AUDIO -> receiveAudioTriggeredVideo(file, triggerContext, upload);
                case KEYPAD -> receiveKeypadTriggeredVideo(file, triggerContext, upload);
            };
            if (upload != null) journal.complete(upload, result);
            return result;
        } finally {
            if (upload != null) journal.release(upload);
        }
    }

    // ============================================
    // AUDIO
    // ============================================

    private VideoReceipt receiveAudioTriggeredVideo(
            MediaFile file,
            VideoTriggerContext triggerContext,
            VideoUploadJournal.Upload upload
    ) {

        String eventId =
                resolveAudioEventId(triggerContext);

        if (eventId == null || eventId.isBlank()) {

            throw new BusinessException(
                    DetectionErrorCode.EVENT_ID_REQUIRED
            );
        }

        SecurityEvent event =
                securityEventRepository
                        .findByEventId(eventId)
                        .orElseThrow(
                                () -> new BusinessException(
                                        SecurityEventErrorCode
                                                .SECURITY_EVENT_NOT_FOUND
                                )
                        );

        // Legacy uploads only carry eventId. Reuse the trigger recorded before camera execution.
        event.connectAudioTrigger(eventId, parseTriggeredAtIfPresent(triggerContext.triggeredAt()));
        VideoTriggerContext audioContext = new VideoTriggerContext(
                eventId, event.getTriggerId(), TriggerType.AUDIO, eventId,
                event.getTriggeredAt() == null ? null : event.getTriggeredAt().toString());

        validateRequiredTriggerFields(audioContext);

        try {

            VisionAnalysis vision = analyzeVideo(file, audioContext, upload);
            validateVisionResult(vision, TriggerType.AUDIO);
            MediaStoragePort.StoredMedia stored = storeVideo(eventId, file, upload);
            if (upload != null && "COMPLETED".equals(event.getStatus())) {
                return new VideoReceipt(eventId, stored.filename(), vision);
            }
            event.updateVideoPath(stored.path());
            securityEventRepository.save(event);

            boolean requiresRiskAssessment = vision.requiresAudioRiskAssessment();

            event.updateVisionResult(
                    vision.prediction(),
                    vision.confidence(),
                    vision.analysisMode(),
                    objectMapper.writeValueAsString(
                            vision.classProbabilities()
                    ),
                    objectMapper.writeValueAsString(vision.visionEvents()),
                    vision.personCount(),
                    vision.videoDurationSeconds(),
                    vision.analyzedAt()
            );

            if (!requiresRiskAssessment) {
                event.completeWithoutRisk();
            }
            securityEventRepository.save(event);

            if (requiresRiskAssessment) {
                analyzeAgentRiskUseCase.analyze(eventId);
            }

            return new VideoReceipt(
                    eventId,
                    stored.filename(),
                    vision
            );

        } catch (RuntimeException exception) {

            event.fail();

            securityEventRepository.save(event);

            throw exception;
        }
    }

    // ============================================
    // KEYPAD
    // ============================================

    private VideoReceipt receiveKeypadTriggeredVideo(
            MediaFile file,
            VideoTriggerContext triggerContext,
            VideoUploadJournal.Upload upload
    ) {

        validateRequiredTriggerFields(triggerContext);
        String triggerId = triggerContext.triggerId();
        OffsetDateTime triggeredAt = OffsetDateTime.parse(triggerContext.triggeredAt());

        /*
         * KEYPAD 자체는 SecurityEvent가 아니다.
         */
        VisionAnalysis vision = analyzeVideo(file, triggerContext, upload);
        validateVisionResult(vision, TriggerType.KEYPAD);
        MediaStoragePort.StoredMedia stored = storeVideo(triggerId, file, upload);

        if (!vision.hasVisionEvents()) {
            return new VideoReceipt(null, stored.filename(), vision);
        }

        String eventId = recordKeypadVisionInputPort.record(new RecordKeypadVisionInputPort.Command(
                triggerId, triggeredAt, stored.path(), vision.prediction(), vision.confidence(),
                vision.analysisMode(),
                objectMapper.writeValueAsString(vision.classProbabilities()),
                objectMapper.writeValueAsString(vision.visionEvents()), vision.personCount(),
                vision.videoDurationSeconds(), vision.analyzedAt()));

        if (vision.hasHighRiskKeypadEvent()) {
            boolean completed = upload != null && securityEventRepository.findByEventId(eventId)
                    .map(event -> "COMPLETED".equals(event.getStatus())).orElse(false);
            if (!completed) {
                if (upload != null) securityEventRepository.findByEventId(eventId).ifPresent(event -> {
                    event.updateStatus("VISION_ANALYZED");
                    securityEventRepository.save(event);
                });
                analyzeAgentRiskUseCase.analyze(eventId);
            }
        }
        return new VideoReceipt(eventId, stored.filename(), vision);
    }

    // ============================================
    // Validation
    // ============================================

    private VisionAnalysis analyzeVideo(MediaFile file, VideoTriggerContext context,
            VideoUploadJournal.Upload upload) {
        if (upload != null && upload.vision() != null) return upload.vision();
        VisionAnalysis vision = visionAnalysisPort.analyze(file, context);
        validateVisionResult(vision, context.triggerType());
        if (upload != null) journal.analyzed(upload, vision);
        return vision;
    }

    private MediaStoragePort.StoredMedia storeVideo(String id, MediaFile file,
            VideoUploadJournal.Upload upload) {
        if (upload != null && upload.stored() != null) return upload.stored();
        var stored = mediaStoragePort.saveVideo(id, file);
        if (upload != null) journal.stored(upload, stored);
        return stored;
    }

    private void validateFile(
            MediaFile file
    ) {

        if (file == null
                || file.size() == 0) {

            throw new BusinessException(
                    DetectionErrorCode.EMPTY_VIDEO_FILE
            );
        }

        if (file.size() > MAX_VIDEO_SIZE) {

            throw new BusinessException(
                    DetectionErrorCode.VIDEO_FILE_TOO_LARGE
            );
        }
    }

    // ============================================
    // 기존 AUDIO 호환
    // ============================================

    private String resolveAudioEventId(
            VideoTriggerContext triggerContext
    ) {

        if (triggerContext.securityEventId() != null
                && !triggerContext.securityEventId().isBlank()) {

            return triggerContext.securityEventId();
        }

        return triggerContext.eventId();
    }

    private void validateRequiredTriggerFields(VideoTriggerContext triggerContext) {
        if (triggerContext.triggerId() == null || triggerContext.triggerId().isBlank()) {
            throw new BusinessException(DetectionErrorCode.TRIGGER_ID_REQUIRED);
        }
        if (triggerContext.triggeredAt() == null || triggerContext.triggeredAt().isBlank()) {
            throw new BusinessException(DetectionErrorCode.TRIGGERED_AT_REQUIRED);
        }
        try {
            OffsetDateTime.parse(triggerContext.triggeredAt());
        } catch (RuntimeException exception) {
            throw new BusinessException(DetectionErrorCode.TRIGGERED_AT_REQUIRED, exception);
        }
    }

    private OffsetDateTime parseTriggeredAtIfPresent(String triggeredAt) {
        if (triggeredAt == null || triggeredAt.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(triggeredAt);
        } catch (RuntimeException exception) {
            throw new BusinessException(DetectionErrorCode.TRIGGERED_AT_REQUIRED, exception);
        }
    }

    private void validateVisionResult(VisionAnalysis vision, TriggerType triggerType) {
        if (vision == null) {
            throw new BusinessException(DetectionErrorCode.VISION_ANALYSIS_FAILED);
        }

        boolean valid = triggerType == TriggerType.AUDIO
                ? isValidAudioVisionResult(vision)
                : vision.behavior() == null;

        if (!valid) {
            throw new BusinessException(DetectionErrorCode.VISION_RESPONSE_CONTRACT_INVALID);
        }
    }

    private boolean isValidAudioVisionResult(VisionAnalysis vision) {
        String analysisMode = vision.analysisMode();
        if (analysisMode == null || analysisMode.isBlank() || vision.isTemporalTrial6()) {
            return isValidTemporalTrial6Result(vision);
        }
        if (vision.isExhibitionRules()) {
            return vision.visionEvents().stream()
                    .allMatch(event -> event != null && event.isExhibitionRulesEvent());
        }
        return false;
    }

    private boolean isValidTemporalTrial6Result(VisionAnalysis vision) {
        if (vision.behavior() == null || vision.prediction() == null || vision.hasVisionEvents()) {
            return false;
        }
        return switch (vision.prediction().toUpperCase(java.util.Locale.ROOT)) {
            case "N1", "A17", "A18", "A19", "A21" -> true;
            default -> false;
        };
    }
}

