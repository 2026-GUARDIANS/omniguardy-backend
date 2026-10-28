package com.omniguardy.backend.domain.securityevent.presentation;

import com.omniguardy.backend.domain.securityevent.application.model.*;
import com.omniguardy.backend.domain.securityevent.application.port.in.QuerySecurityEventsInputPort;
import lombok.RequiredArgsConstructor;
import com.omniguardy.backend.global.security.auth.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security-events")
@RequiredArgsConstructor
public class SecurityEventController {
    private final QuerySecurityEventsInputPort query;

    @GetMapping
    public SecurityEventPageResponse list(@AuthenticationPrincipal CustomUserDetails principal, @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return query.list(principal.getUserId(), page, size);
    }

    @GetMapping("/{eventId}")
    public SecurityEventResponse detail(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable String eventId) {
        return query.detail(principal.getUserId(), eventId);
    }
}
