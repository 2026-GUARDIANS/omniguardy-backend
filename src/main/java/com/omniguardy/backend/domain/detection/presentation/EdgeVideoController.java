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
import com.omniguardy.backend.global.config.OpenApiSchemas;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/edge")
@Tag(name = "Edge Detection", description = "라즈베리파이 음성·영상 감지 API")
public class EdgeVideoController {

    private final ReceiveVideoInputPort receiveVideoInputPort;
    private final DetectionPresentationMapper mapper;

    @Operation(summary = "영상 파일 분석",
            description = "AUDIO 또는 KEYPAD 트리거로 촬영된 영상을 분석하고 보안 이벤트에 연결합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "영상 분석 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.VideoUploadEnvelope.class),
                            examples = @ExampleObject(name = "VIDEO_ANALYZED", value = VIDEO_UPLOAD_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파일 또는 트리거 파라미터 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "연결할 사용자 또는 보안 이벤트 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "SECURITY_EVENT_NOT_FOUND", value = SECURITY_EVENT_NOT_FOUND))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동일 트리거가 이미 처리 중이거나 다른 영상으로 처리됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VIDEO_UPLOAD_CONFLICT", value = VIDEO_UPLOAD_CONFLICT))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "100MB를 초과한 영상",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VIDEO_FILE_TOO_LARGE", value = VIDEO_TOO_LARGE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Vision 분석 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "VISION_ANALYSIS_FAILED", value = VISION_ANALYSIS_FAILED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "영상 저장 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "MEDIA_STORAGE_FAILED", value = MEDIA_STORAGE_FAILED)))
    })
    @PostMapping(
            value = "/video",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<VideoUploadResponseDto>> uploadVideo(

            @Parameter(description = "MP4 등 분석할 영상 파일(최대 100MB)", required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file")
            MultipartFile file,

            @Parameter(description = "AUDIO 트리거 보안 이벤트 ID", example = "evt-audio-20261029-001")
            @RequestParam(
                    value = "eventId",
                    required = false
            )
            String eventId,

            @Parameter(description = "트리거 고유 ID", example = "trigger-keypad-001")
            @RequestParam(
                    value = "triggerId",
                    required = false
            )
            String triggerId,

            @Parameter(description = "트리거 유형", example = "KEYPAD",
                    schema = @Schema(allowableValues = {"AUDIO", "KEYPAD"}))
            @RequestParam(
                    value = "triggerType",
                    required = false
            )
            String triggerType,

            @Parameter(description = "KEYPAD 트리거로 미리 생성된 보안 이벤트 ID", example = "security-event-001")
            @RequestParam(
                    value = "securityEventId",
                    required = false
            )
            String securityEventId,

            @Parameter(description = "트리거 발생 시각(ISO-8601)", example = "2026-10-29T20:10:15+09:00")
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
