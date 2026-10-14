package com.omniguardy.backend.domain.user.infrastructure.persistence;

import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {
    private final SpringDataUserRepository repository;

    @Override public User save(User user) { return repository.save(user); }
    @Override public Optional<User> findById(Long id) { return repository.findById(id); }
    @Override public Optional<User> findByEmail(String email) { return repository.findByEmail(email); }
    @Override public boolean existsByEmail(String email) { return repository.existsByEmail(email); }
}
