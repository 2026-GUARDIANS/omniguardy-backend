package com.omniguardy.backend.domain.visionsession.presentation;

import com.omniguardy.backend.domain.visionsession.application.port.in.ReceiveVisionEarlyEventInputPort;
import com.omniguardy.backend.domain.visionsession.presentation.dto.VisionEarlyEventRequest;
import com.omniguardy.backend.global.config.OpenApiConfig;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/ai/vision/early-events")
@SecurityRequirement(name = OpenApiConfig.VISION_CALLBACK_API_KEY)
public class VisionEarlyEventController {
    private final ReceiveVisionEarlyEventInputPort earlyEvents;

    @Operation(summary = "Record a provisional streaming Vision EARLY_EVENT",
            description = "Store only. Identical retries return 204; changed payload for the same identifier returns 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Stored or identical retry", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid EARLY_EVENT JSON", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Vision callback key required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session or issued trigger not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Binding or duplicate payload conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> receive(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = VisionEarlyEventRequest.class)))
            @RequestBody JsonNode body) {
        earlyEvents.receive(VisionEarlyEventRequest.from(body).toDomain());
        return ResponseEntity.noContent().build();
    }
}
