package com.omniguardy.backend.domain.user.application.usecase;

import com.omniguardy.backend.domain.user.domain.error.UserErrorCode;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DemoUserResolver {
    private final UserRepository userRepository;
    private final Environment environment;

    public Optional<User> resolve() {
        Long userId = environment.getProperty("omniguardy.demo.user-id", Long.class);
        if (userId == null) throw new BusinessException(UserErrorCode.DEMO_USER_ID_NOT_CONFIGURED);
        return Optional.of(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND)));
    }
}
