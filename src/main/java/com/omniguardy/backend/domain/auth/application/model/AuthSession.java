package com.omniguardy.backend.domain.auth.application.model;

public record AuthSession(
        String accessToken,
        String refreshToken,
        long refreshTokenMaxAgeSeconds,
        Long userId,
        String email,
        String name
) {
}
