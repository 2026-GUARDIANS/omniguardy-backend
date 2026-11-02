package com.omniguardy.backend.domain.visionsession.presentation;

import com.omniguardy.backend.domain.visionsession.application.model.VisionProcessingView;
import com.omniguardy.backend.domain.visionsession.application.port.in.CompleteVisionSessionInputPort;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.success.SuccessResponse;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/edge/vision/sessions")
@SecurityRequirement(name = com.omniguardy.backend.global.config.OpenApiConfig.DEVICE_API_KEY)
public class VisionSessionCompletionController {
    private final CompleteVisionSessionInputPort completion;

    @Operation(summary = "Notify that Pi has completed streaming Vision input",
            description = "Pi must call Vision /vision/complete first. No request body. Durably enqueue polling; identical retries never restart the job.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Accepted, including repeated/terminal notifications"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid session ID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Device key required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session or trigger not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Session already terminal without a polling job", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<VisionProcessingView>> complete(@PathVariable String sessionId) {
        return SuccessResponse.of(VisionSessionSuccessCode.VISION_COMPLETION_ACCEPTED, completion.complete(sessionId));
    }
}
