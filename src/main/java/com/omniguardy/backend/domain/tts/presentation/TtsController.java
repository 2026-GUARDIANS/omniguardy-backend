package com.omniguardy.backend.domain.tts.presentation;

import com.omniguardy.backend.domain.tts.application.usecase.PublishTtsUseCase;
import com.omniguardy.backend.domain.tts.presentation.dto.request.TtsRequestDto;
import com.omniguardy.backend.domain.tts.presentation.mapper.TtsPresentationMapper;
import com.omniguardy.backend.domain.tts.presentation.success.TtsSuccessCode;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tts")
@Tag(name = "TTS", description = "도어락 스피커 TTS 안내 API")
public class TtsController {
    private final PublishTtsUseCase publishTtsUseCase;
    private final TtsPresentationMapper mapper;

    @Operation(summary = "TTS 안내 방송", description = "입력한 문구를 음성으로 생성해 도어락 MQTT 채널로 전송합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = TtsRequestDto.class),
                            examples = @ExampleObject(name = "TTS 요청", value = TTS_REQUEST))))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "TTS 전송 성공",
                    content = @Content(schema = @Schema(implementation = OpenApiSchemas.EmptySuccessEnvelope.class),
                            examples = @ExampleObject(name = "TTS_MESSAGE_PUBLISHED", value = TTS_SUCCESS))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "빈 문구 또는 100자 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "AUTHENTICATION_REQUIRED", value = AUTHENTICATION_REQUIRED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "TTS 생성 또는 MQTT 전송 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "TTS_DELIVERY_FAILED", value = TTS_DELIVERY_FAILED))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "현재 환경에서 TTS 비활성화",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "TTS_DISABLED", value = TTS_DISABLED)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> publishTts(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TtsRequestDto request) {
        publishTtsUseCase.publish(mapper.toCommand(request));
        return SuccessResponse.of(TtsSuccessCode.MESSAGE_PUBLISHED, null);
    }
}
