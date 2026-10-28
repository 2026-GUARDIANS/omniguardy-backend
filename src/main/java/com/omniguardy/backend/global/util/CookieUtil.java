package com.omniguardy.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

public class CookieUtil {

    private CookieUtil() {
    }

    public static void addRefreshTokenCookie(
            HttpServletResponse response,
            String refreshToken,
            long maxAgeSeconds
    ) {
        addRefreshTokenCookie(response, refreshToken, maxAgeSeconds, false, "Lax");
    }

    public static void addRefreshTokenCookie(HttpServletResponse response, String refreshToken,
            long maxAgeSeconds, boolean secure, String sameSite) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(secure) // HTTPS 배포 시 true
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public static void deleteRefreshTokenCookie(HttpServletResponse response) {
        deleteRefreshTokenCookie(response, false, "Lax");
    }

    public static void deleteRefreshTokenCookie(HttpServletResponse response, boolean secure, String sameSite) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
