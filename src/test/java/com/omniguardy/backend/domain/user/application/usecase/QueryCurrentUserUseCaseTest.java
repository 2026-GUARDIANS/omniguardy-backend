package com.omniguardy.backend.domain.user.application.usecase;

import com.omniguardy.backend.domain.user.domain.model.User;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryCurrentUserUseCaseTest {
    @Test
    void returnsOnlyProfileFieldsForAuthenticatedUser() {
        var repository = mock(UserRepository.class);
        var useCase = new QueryCurrentUserUseCase(repository);
        var user = User.builder().email("guard@example.com").password("secret")
                .name("김가디").phoneNumber("010-1234-5678").build();
        when(repository.findById(17L)).thenReturn(Optional.of(user));

        var response = useCase.query(17L);

        assertEquals("김가디", response.name());
        assertEquals("guard@example.com", response.email());
        assertEquals("010-1234-5678", response.phoneNumber());
    }

    @Test
    void rejectsMissingOrUnknownUser() {
        var repository = mock(UserRepository.class);
        var useCase = new QueryCurrentUserUseCase(repository);
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> useCase.query(null));
        assertThrows(BusinessException.class, () -> useCase.query(99L));
        verify(repository, never()).findById(null);
    }
}
