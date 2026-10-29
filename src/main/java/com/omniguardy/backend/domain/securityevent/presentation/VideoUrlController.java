package com.omniguardy.backend.domain.securityevent.presentation;

import com.omniguardy.backend.domain.securityevent.application.port.in.QueryVideoUrlInputPort;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@Profile("prod")
@RequiredArgsConstructor
public class VideoUrlController {
    private final QueryVideoUrlInputPort query;
    @GetMapping("/api/security-events/{eventId}/video-url")
    public ResponseEntity<Map<String, String>> query(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String eventId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("url", query.query(eventId, user.getUserId())));
    }
}
