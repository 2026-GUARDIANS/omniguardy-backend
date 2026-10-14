package com.omniguardy.backend.domain.notification.infrastructure.persistence;

import com.omniguardy.backend.domain.notification.domain.model.Notification;
import com.omniguardy.backend.domain.user.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface SpringDataNotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserOrderByCreatedAtDesc(User user);
}
