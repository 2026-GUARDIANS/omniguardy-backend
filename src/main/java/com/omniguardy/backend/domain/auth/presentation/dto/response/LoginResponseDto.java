package com.omniguardy.backend.domain.auth.presentation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponseDto {

    private String accessToken;
    private String tokenType;
    private Long userId;
    private String email;
    private String name;
}

