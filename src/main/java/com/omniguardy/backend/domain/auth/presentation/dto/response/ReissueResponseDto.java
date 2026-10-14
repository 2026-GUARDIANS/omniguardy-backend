package com.omniguardy.backend.domain.auth.presentation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReissueResponseDto {

    private String accessToken;
    private String tokenType;
}

