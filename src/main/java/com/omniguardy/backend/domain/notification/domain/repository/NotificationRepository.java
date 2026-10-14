package com.omniguardy.backend.domain.notification.domain.repository;

import com.omniguardy.backend.domain.notification.domain.model.Notification;
import com.omniguardy.backend.domain.user.domain.model.User;

import java.util.List;

public interface NotificationRepository {
    Notification save(Notification notification);
    List<Notification> findAllByUserOrderByCreatedAtDesc(User user);
}

