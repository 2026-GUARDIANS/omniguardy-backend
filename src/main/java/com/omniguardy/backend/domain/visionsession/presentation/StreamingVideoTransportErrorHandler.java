package com.omniguardy.backend.domain.visionsession.presentation;

import com.omniguardy.backend.global.error.exception.GlobalExceptionHandler;
import com.omniguardy.backend.global.error.response.ErrorResponse;
import com.omniguardy.backend.domain.visionsession.domain.error.StreamingVideoErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Also handles multipart rejection before a controller is selected; other APIs retain their existing handler. */
@RestControllerAdvice @Order(Ordered.HIGHEST_PRECEDENCE) @RequiredArgsConstructor
public class StreamingVideoTransportErrorHandler {
    private final GlobalExceptionHandler existingHandler;

    @ExceptionHandler({MaxUploadSizeExceededException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handle(Exception failure, HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.matches("/api/edge/vision/sessions/[^/]+/video/?")) return existingHandler.handleUnexpectedException(failure);
        var code = failure instanceof MaxUploadSizeExceededException
                ? StreamingVideoErrorCode.STREAMING_VIDEO_TOO_LARGE : StreamingVideoErrorCode.INVALID_STREAMING_MP4;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.from(code));
    }
}
