package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.TriggerType;
import com.omniguardy.backend.domain.detection.application.model.VideoReceipt;
import com.omniguardy.backend.domain.detection.application.model.VideoTriggerContext;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveVideoInputPort;
import com.omniguardy.backend.domain.detection.domain.error.DetectionErrorCode;
import com.omniguardy.backend.domain.detection.presentation.dto.response.VideoUploadResponseDto;
import com.omniguardy.backend.domain.detection.presentation.mapper.DetectionPresentationMapper;
import com.omniguardy.backend.domain.detection.presentation.success.DetectionSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import com.omniguardy.backend.global.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/edge")
public class EdgeVideoController {

    private final ReceiveVideoInputPort receiveVideoInputPort;
    private final DetectionPresentationMapper mapper;

    @PostMapping(
            value = "/video",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<VideoUploadResponseDto>> uploadVideo(

            @RequestPart("file")
            MultipartFile file,

            @RequestParam(
                    value = "eventId",
                    required = false
            )
            String eventId,

            @RequestParam(
                    value = "triggerId",
                    required = false
            )
            String triggerId,

            @RequestParam(
                    value = "triggerType",
                    required = false
            )
            String triggerType,

            @RequestParam(
                    value = "securityEventId",
                    required = false
            )
            String securityEventId,

            @RequestParam(
                    value = "triggeredAt",
                    required = false
            )
            String triggeredAt

    ) throws IOException {

        log.info("video_perf stage=receive_start at={} eventId={} triggerId={} triggerType={} sizeBytes={}",
                Instant.now(), eventId, triggerId, triggerType == null || triggerType.isBlank() ? "AUDIO" : triggerType,
                file.getSize());

        TriggerType resolvedTriggerType;

        /*
         * 기존 Raspberry Pi AUDIO 요청:
         *
         * file
         * eventId
         *
         * triggerType은 없음.
         */
        if (triggerType == null || triggerType.isBlank()) {
            if (eventId == null || eventId.isBlank()) {
                throw new BusinessException(DetectionErrorCode.EVENT_ID_REQUIRED);
            }

            resolvedTriggerType = TriggerType.AUDIO;

        } else {

            try {
                resolvedTriggerType = TriggerType.valueOf(triggerType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(GlobalErrorCode.INVALID_REQUEST, exception);
            }
        }

        VideoTriggerContext triggerContext =
                new VideoTriggerContext(
                        eventId,
                        triggerId,
                        resolvedTriggerType,
                        securityEventId,
                        triggeredAt
                );

        if (file.isEmpty()) throw new BusinessException(DetectionErrorCode.EMPTY_VIDEO_FILE);
        if (file.getSize() > 100L * 1024 * 1024) throw new BusinessException(DetectionErrorCode.VIDEO_FILE_TOO_LARGE);
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile("omniguardy-video-", ".upload");
        VideoReceipt result;
        try {
            file.transferTo(temporary);
            result = receiveVideoInputPort.receive(
                    com.omniguardy.backend.domain.detection.application.model.MediaFile.fromPath(
                            file.getOriginalFilename(), file.getContentType(), temporary), triggerContext);
        } finally {
            java.nio.file.Files.deleteIfExists(temporary);
        }

        return SuccessResponse.of(
                DetectionSuccessCode.VIDEO_ANALYZED,
                mapper.toResponse(result)
        );
    }
}
