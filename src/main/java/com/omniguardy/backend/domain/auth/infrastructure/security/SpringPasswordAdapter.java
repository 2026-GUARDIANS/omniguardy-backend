package com.omniguardy.backend.domain.auth.infrastructure.security;

import com.omniguardy.backend.domain.auth.application.port.out.PasswordPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringPasswordAdapter implements PasswordPort {
    private final PasswordEncoder passwordEncoder;

    @Override public String encode(String rawPassword) { return passwordEncoder.encode(rawPassword); }
    @Override public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
