package com.omniguardy.backend.domain.user.presentation.dto.response;

import com.omniguardy.backend.domain.user.application.model.CurrentUserProfile;
import io.swagger.v3.oas.annotations.media.Schema;

public record CurrentUserResponse(
        @Schema(example = "김가디") String name,
        @Schema(example = "guard@example.com") String email,
        @Schema(example = "010-1234-5678") String phoneNumber
) {
    public static CurrentUserResponse from(CurrentUserProfile profile) {
        return new CurrentUserResponse(profile.name(), profile.email(), profile.phoneNumber());
    }
}
