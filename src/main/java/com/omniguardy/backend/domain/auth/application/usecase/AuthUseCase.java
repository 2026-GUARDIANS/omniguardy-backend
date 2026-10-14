package com.omniguardy.backend.domain.auth.application.usecase;

import com.omniguardy.backend.domain.auth.application.model.AuthSession;
import com.omniguardy.backend.domain.auth.application.model.LoginCommand;
import com.omniguardy.backend.domain.auth.application.model.SignupCommand;
import com.omniguardy.backend.domain.auth.application.port.out.PasswordPort;
import com.omniguardy.backend.domain.auth.application.port.out.TokenPort;
import com.omniguardy.backend.domain.auth.domain.error.AuthErrorCode;
import com.omniguardy.backend.domain.user.domain.error.UserErrorCode;
import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthUseCase {
    private final UserRepository userRepository;
    private final PasswordPort passwordPort;
    private final TokenPort tokenPort;

    @Transactional
    public AuthSession signup(SignupCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.builder().email(command.email()).password(passwordPort.encode(command.password()))
                .name(command.name()).phoneNumber(command.phoneNumber()).build();
        return issueSession(userRepository.save(user));
    }

    @Transactional
    public AuthSession login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_LOGIN_INFO));
        if (!passwordPort.matches(command.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_LOGIN_INFO);
        }
        return issueSession(user);
    }

    @Transactional
    public AuthSession reissue(String refreshToken) {
        if (refreshToken == null) throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        tokenPort.validate(refreshToken);
        User user = userRepository.findById(tokenPort.extractUserId(refreshToken))
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        if (user.getRefreshToken() == null) throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND);
        if (!user.getRefreshToken().equals(refreshToken)) {
            user.clearRefreshToken();
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_MISMATCH);
        }
        if (user.getRefreshTokenExpiredAt().isBefore(LocalDateTime.now())) {
            user.clearRefreshToken();
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return issueSession(user);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        user.clearRefreshToken();
    }

    private AuthSession issueSession(User user) {
        String accessToken = tokenPort.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = tokenPort.createRefreshToken(user.getId(), user.getEmail());
        long maxAge = tokenPort.refreshTokenExpirationSeconds();
        user.updateRefreshToken(refreshToken, LocalDateTime.now().plusSeconds(maxAge));
        return new AuthSession(accessToken, refreshToken, maxAge, user.getId(), user.getEmail(), user.getName());
    }
}
