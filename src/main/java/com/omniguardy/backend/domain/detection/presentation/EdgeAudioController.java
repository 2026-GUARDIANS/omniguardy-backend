package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.AudioReceipt;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioInputPort;
import com.omniguardy.backend.domain.detection.presentation.dto.response.AudioUploadResponseDto;
import com.omniguardy.backend.domain.detection.presentation.mapper.DetectionPresentationMapper;
import com.omniguardy.backend.domain.detection.presentation.success.DetectionSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/edge")
@Tag(name = "Edge Detection", description = "라즈베리파이 음성·영상 감지 API")
public class EdgeAudioController {
    private final ReceiveAudioInputPort receiveAudioUseCase;
    private final DetectionPresentationMapper mapper;

    @Operation(summary = "음성 파일 분석", description = "도어락에서 수집한 음성을 분석하고 감지 이벤트를 생성합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "음성 분석 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.AudioUploadEnvelope.class),
                            examples = @ExampleObject(name = "AUDIO_RECEIVED", value = AUDIO_UPLOAD_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "파일 누락 또는 빈 음성 파일",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Audio 분석 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUDIO_ANALYSIS_FAILED", value = AUDIO_ANALYSIS_FAILED)))
    })
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AudioUploadResponseDto>> uploadAudio(
            @Parameter(description = "WAV 등 분석할 음성 파일", required = true,
                    schema = @Schema(type = "string", format = "binary"))
            @RequestPart("file") MultipartFile file) throws IOException {
        AudioReceipt result = receiveAudioUseCase.receive(mapper.toMediaFile(file));
        return SuccessResponse.of(DetectionSuccessCode.AUDIO_RECEIVED, mapper.toResponse(result));
    }
}
