package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.model.AudioDetectionResult.Outcome;
import com.omniguardy.backend.domain.detection.application.port.in.ReceiveAudioEventInputPort;
import com.omniguardy.backend.domain.detection.presentation.dto.request.AudioEventRequestDto;
import com.omniguardy.backend.domain.detection.presentation.success.DetectionSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.success.SuccessResponse;
import com.omniguardy.backend.global.config.OpenApiSchemas;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/ai")
@Tag(name = "Internal AI", description = "내부 AI 분석 서버 Callback API")
public class AudioEventController {
    private final ReceiveAudioEventInputPort receiveAudioEvent;

    @Operation(summary = "Audio 분석 결과 수신",
            description = "FastAPI가 분석한 음향 분류 결과를 저장합니다. X-Device-Api-Key 헤더가 필요합니다.",
            security = @SecurityRequirement(name = com.omniguardy.backend.global.config.OpenApiConfig.DEVICE_API_KEY),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AudioEventRequestDto.class),
                            examples = @ExampleObject(name = "노크 감지", value = AUDIO_RESULT_REQUEST))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "분석 결과 저장 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.AudioResultEnvelope.class),
                            examples = @ExampleObject(name = "AUDIO_RESULT_RECEIVED", value = AUDIO_RESULT_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "식별자, 분류 또는 확률 범위 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "장치 API Key 누락 또는 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED)))
    })
    @PostMapping(value = "/audio-result", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AudioEventResponse>> receive(@Valid @RequestBody AudioEventRequestDto request) {
        var result = receiveAudioEvent.receive(request.detectionId(), request.toAnalysis());
        return SuccessResponse.of(DetectionSuccessCode.AUDIO_RESULT_RECEIVED,
                new AudioEventResponse(request.detectionId(), result.eventId(), result.outcome()));
    }

    public record AudioEventResponse(String detectionId, String eventId, Outcome outcome) {}
}
