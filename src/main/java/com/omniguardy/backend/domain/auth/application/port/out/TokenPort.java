package com.omniguardy.backend.domain.auth.application.port.out;

public interface TokenPort {
    String createAccessToken(Long userId, String email);
    String createRefreshToken(Long userId, String email);
    void validate(String token);
    Long extractUserId(String token);
    long refreshTokenExpirationSeconds();
}
