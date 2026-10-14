package com.omniguardy.backend.domain.notification.application.usecase;

import com.omniguardy.backend.domain.notification.application.model.RegisterDeviceTokenCommand;
import com.omniguardy.backend.domain.notification.domain.model.DeviceToken;
import com.omniguardy.backend.domain.notification.domain.repository.DeviceTokenRepository;
import com.omniguardy.backend.domain.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public void saveDeviceToken(User user, RegisterDeviceTokenCommand command) {
        deviceTokenRepository.findByToken(command.token())
                .ifPresentOrElse(
                        DeviceToken::activate,
                        () -> deviceTokenRepository.save(
                                DeviceToken.builder()
                                        .user(user)
                                        .token(command.token())
                                        .deviceType(command.deviceType())
                                        .active(true)
                                        .build()
                        )
                );
    }
}

