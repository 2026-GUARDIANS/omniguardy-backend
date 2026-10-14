package com.omniguardy.backend.domain.notification.infrastructure.persistence;

import com.omniguardy.backend.domain.notification.domain.model.DeviceToken;
import com.omniguardy.backend.domain.notification.domain.repository.DeviceTokenRepository;
import com.omniguardy.backend.domain.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DeviceTokenRepositoryAdapter implements DeviceTokenRepository {
    private final SpringDataDeviceTokenRepository repository;
    @Override public DeviceToken save(DeviceToken token) { return repository.save(token); }
    @Override public Optional<DeviceToken> findByToken(String token) { return repository.findByToken(token); }
    @Override public List<DeviceToken> findAllByUserAndActiveTrue(User user) {
        return repository.findAllByUserAndActiveTrue(user);
    }
}
