package com.omniguardy.backend.domain.user.application.port.in;

import com.omniguardy.backend.domain.user.application.model.CurrentUserProfile;

public interface QueryCurrentUserInputPort {
    CurrentUserProfile query(Long userId);
}
