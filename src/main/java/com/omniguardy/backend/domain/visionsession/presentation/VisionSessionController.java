package com.omniguardy.backend.domain.visionsession.presentation;

import com.omniguardy.backend.domain.visionsession.application.port.in.VisionSessionInputPort;
import com.omniguardy.backend.domain.visionsession.domain.model.*;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.success.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import static com.omniguardy.backend.domain.visionsession.presentation.VisionSessionSuccessCode.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/edge/vision/sessions")
@Tag(name = "Streaming Vision Sessions")
@SecurityRequirement(name = com.omniguardy.backend.global.config.OpenApiConfig.DEVICE_API_KEY)
public class VisionSessionController {
    private final VisionSessionInputPort sessions;

    public record RegisterRequest(@NotBlank @Size(max = 128) String sessionId,
                                  @NotNull VisionTriggerType triggerType,
                                  @Size(max = 36) String eventId, @Size(max = 64) String triggerId) {}

    @Operation(summary = "Register a streaming Vision session",
            description = "AUDIO requires eventId only; KEYPAD requires triggerId only. New: 201; identical retry: 200; binding conflict: 409.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Session registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Identical registration replayed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or trigger",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Device key required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Trigger not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Session or trigger already bound",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ApiResponse<VisionSession>> register(@Valid @RequestBody RegisterRequest request) {
        var registration = sessions.register(request.sessionId(), request.triggerType(), request.eventId(), request.triggerId());
        if (!registration.created()) return SuccessResponse.of(VISION_SESSION_FOUND, registration.session());
        return ResponseEntity.created(URI.create("/api/edge/vision/sessions/" + registration.session().sessionId()))
                .body(ApiResponse.success(VISION_SESSION_REGISTERED, registration.session()));
    }

    @Operation(summary = "Get a streaming Vision session")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid session ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Device key required",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<VisionSession>> find(@PathVariable String sessionId) {
        return SuccessResponse.of(VISION_SESSION_FOUND, sessions.find(sessionId));
    }
}
