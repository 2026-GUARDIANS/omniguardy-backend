package com.omniguardy.backend.domain.notification.domain.repository;

import com.omniguardy.backend.domain.notification.domain.model.DeviceToken;
import com.omniguardy.backend.domain.user.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository {
    DeviceToken save(DeviceToken deviceToken);
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findAllByUserAndActiveTrue(User user);
}

