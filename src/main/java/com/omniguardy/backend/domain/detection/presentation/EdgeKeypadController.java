package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.port.in.TriggerKeypadCameraUseCase;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.omniguardy.backend.global.config.OpenApiExamples.INTERNAL_SERVER_ERROR;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/keypad")
@Tag(name = "Edge Detection", description = "라즈베리파이 음성·영상 감지 API")
public class EdgeKeypadController {

    private final TriggerKeypadCameraUseCase triggerKeypadCameraUseCase;

    @Operation(summary = "키패드 카메라 촬영 요청", description = "MQTT로 도어락 카메라 촬영 명령을 전송합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "촬영 요청 성공", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "MQTT 명령 전송 중 서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "INTERNAL_SERVER_ERROR", value = INTERNAL_SERVER_ERROR)))
    })
    @PostMapping("/trigger")
    public ResponseEntity<Void> trigger() {

        triggerKeypadCameraUseCase.trigger();

        return ResponseEntity.ok().build();
    }
}
