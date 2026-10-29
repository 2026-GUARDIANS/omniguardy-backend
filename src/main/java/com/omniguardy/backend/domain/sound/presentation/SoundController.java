package com.omniguardy.backend.domain.sound.presentation;

import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.omniguardy.backend.global.config.OpenApiExamples.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sounds")
@Tag(name = "Sound", description = "도어락 경고음 제어 API")
public class SoundController {

    private final PlaySoundUseCase playSoundUseCase;

    @Operation(summary = "경고음 재생", description = "MQTT로 WARNING 또는 SIREN 재생 명령을 전송합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "재생 명령 전송 성공", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "지원하지 않는 사운드 타입",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INVALID_REQUEST", value = INVALID_REQUEST))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "MQTT 명령 전송 중 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_SERVER_ERROR", value = INTERNAL_SERVER_ERROR)))
    })
    @PostMapping("/{type}")
    public ResponseEntity<Void> play(
            @Parameter(description = "재생할 경고음", example = "WARNING", required = true,
                    schema = @Schema(allowableValues = {"WARNING", "SIREN"}))
            @PathVariable PlaySoundType type
    ) {
        playSoundUseCase.play(type);

        return ResponseEntity.noContent().build();
    }
}
