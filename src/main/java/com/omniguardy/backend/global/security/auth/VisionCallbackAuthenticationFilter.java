package com.omniguardy.backend.global.security.auth;

import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.response.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

/** Dedicated server-to-server credential. User JWTs and device credentials are not accepted. */
public class VisionCallbackAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Vision-Callback-Key";
    private final String apiKey;
    private final ErrorResponseWriter errorWriter;

    public VisionCallbackAuthenticationFilter(String apiKey, ErrorResponseWriter errorWriter) {
        this.apiKey = apiKey;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var supplied = Collections.list(request.getHeaders(HEADER));
        if (apiKey == null || apiKey.isBlank() || supplied.size() != 1 || !MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8), supplied.getFirst().getBytes(StandardCharsets.UTF_8))) {
            errorWriter.write(response, GlobalErrorCode.AUTHENTICATION_REQUIRED);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("vision-callback", null,
                List.of(new SimpleGrantedAuthority("VISION_CALLBACK"))));
        SecurityContextHolder.setContext(context);
        try { chain.doFilter(request, response); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
