package com.omniguardy.backend.global.performance;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** Runs before security; measures Spring ingress to response, without reading the body or headers. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PerformanceHttpFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return stage(request) == null; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (var scope = PerformanceLog.request(UUID.randomUUID().toString()); var span = PerformanceLog.start(stage(request))) {
            boolean returned = false;
            try { chain.doFilter(request, response); returned = true; }
            catch (ServletException | IOException | RuntimeException | Error failure) { span.failure(failure); throw failure; }
            finally { if (returned || response.isCommitted()) span.response(response.getStatus()); }
        }
    }
    private String stage(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return null;
        return switch (request.getRequestURI()) {
            case "/api/internal/ai/audio-result" -> "AUDIO_RESULT_HTTP";
            case "/api/edge/vision/sessions" -> "VISION_SESSION_HTTP";
            case "/api/internal/ai/vision/early-events" -> "EARLY_EVENT_HTTP";
            case "/api/edge/audio" -> "LEGACY_AUDIO_HTTP";
            case "/api/edge/video" -> "LEGACY_VIDEO_HTTP";
            case "/api/keypad/trigger" -> "KEYPAD_TRIGGER_HTTP";
            default -> null;
        };
    }
}
