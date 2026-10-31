package com.omniguardy.backend.domain.user.application.usecase;

import com.omniguardy.backend.domain.user.application.model.CurrentUserProfile;
import com.omniguardy.backend.domain.user.application.port.in.QueryCurrentUserInputPort;
import com.omniguardy.backend.domain.user.domain.error.UserErrorCode;
import com.omniguardy.backend.domain.user.domain.repository.UserRepository;
import com.omniguardy.backend.global.error.GlobalErrorCode;
import com.omniguardy.backend.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QueryCurrentUserUseCase implements QueryCurrentUserInputPort {
    private final UserRepository userRepository;

    @Override
    public CurrentUserProfile query(Long userId) {
        if (userId == null) {
            throw new BusinessException(GlobalErrorCode.AUTHENTICATION_REQUIRED);
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return new CurrentUserProfile(user.getName(), user.getEmail(), user.getPhoneNumber());
    }
}
