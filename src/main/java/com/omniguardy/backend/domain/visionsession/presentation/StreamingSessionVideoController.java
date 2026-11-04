package com.omniguardy.backend.domain.visionsession.presentation;

import com.omniguardy.backend.domain.detection.application.model.MediaFile;
import com.omniguardy.backend.domain.visionsession.application.model.*;
import com.omniguardy.backend.domain.visionsession.application.port.in.StreamingSessionVideoInputPort;
import com.omniguardy.backend.global.response.ApiResponse;
import com.omniguardy.backend.global.success.SuccessResponse;
import com.omniguardy.backend.global.error.exception.BusinessException;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import static com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode.*;
import static com.omniguardy.backend.domain.visionsession.presentation.StreamingVideoSuccessCode.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/edge/vision/sessions/{sessionId}/video")
@SecurityRequirement(name = com.omniguardy.backend.global.config.OpenApiConfig.DEVICE_API_KEY)
public class StreamingSessionVideoController {
    private final StreamingSessionVideoInputPort videos;

    @Operation(summary = "Store a streaming session MP4 without analysis",
            description = "Registered session required. MP4 up to 100 MiB. Same bytes replay 200; different bytes or an active upload conflict 409. No Vision/Agent invocation.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Stored; metadata Location"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Identical upload replayed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Empty file, invalid ID or missing part"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Device key required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session or issued trigger not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Content/binding/configuration conflict, a different event video, or upload in progress"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File exceeds 100 MiB"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "Invalid MP4 or request content type"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "Storage error; retry same file"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Database error; retry same file")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StreamingVideoView>> upload(@PathVariable String sessionId,
            @Parameter(required = true, description = "Original MP4, video/mp4, at most 100 MiB",
                    schema = @Schema(type = "string", format = "binary")) @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new BusinessException(EMPTY_STREAMING_VIDEO);
        if (file.getSize() > StreamingVideoFileInfo.MAX_BYTES) throw new BusinessException(STREAMING_VIDEO_TOO_LARGE);
        var temporary = Files.createTempFile("omniguardy-streaming-video-", ".upload");
        try {
            file.transferTo(temporary);
            var receipt = videos.upload(sessionId, MediaFile.fromPath(file.getOriginalFilename(), file.getContentType(), temporary));
            if (!receipt.storedNow()) return SuccessResponse.of(STREAMING_VIDEO_FOUND, receipt.video());
            return ResponseEntity.created(URI.create("/api/edge/vision/sessions/" + sessionId + "/video"))
                    .body(ApiResponse.success(STREAMING_VIDEO_STORED, receipt.video()));
        } finally { Files.deleteIfExists(temporary); }
    }

    @Operation(summary = "Get durable streaming video metadata",
            description = "Device key required. uploadState must be STORED before using the original video. Storage path is a private object key/local path, not a public URL.")
    @GetMapping
    public ResponseEntity<ApiResponse<StreamingVideoView>> find(@PathVariable String sessionId) {
        return SuccessResponse.of(STREAMING_VIDEO_FOUND, videos.find(sessionId));
    }
}
