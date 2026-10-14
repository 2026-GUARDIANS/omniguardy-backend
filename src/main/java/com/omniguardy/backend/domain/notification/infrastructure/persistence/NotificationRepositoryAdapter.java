package com.omniguardy.backend.domain.notification.infrastructure.persistence;

import com.omniguardy.backend.domain.notification.domain.model.Notification;
import com.omniguardy.backend.domain.notification.domain.repository.NotificationRepository;
import com.omniguardy.backend.domain.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {
    private final SpringDataNotificationRepository repository;
    @Override public Notification save(Notification notification) { return repository.save(notification); }
    @Override public List<Notification> findAllByUserOrderByCreatedAtDesc(User user) {
        return repository.findAllByUserOrderByCreatedAtDesc(user);
    }
}
