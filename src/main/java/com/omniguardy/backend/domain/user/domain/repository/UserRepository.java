package com.omniguardy.backend.domain.user.domain.repository;

import com.omniguardy.backend.domain.user.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

