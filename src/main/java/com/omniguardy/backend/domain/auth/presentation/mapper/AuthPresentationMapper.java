package com.omniguardy.backend.domain.auth.presentation.mapper;

import com.omniguardy.backend.domain.auth.application.model.AuthSession;
import com.omniguardy.backend.domain.auth.application.model.LoginCommand;
import com.omniguardy.backend.domain.auth.application.model.SignupCommand;
import com.omniguardy.backend.domain.auth.presentation.dto.request.LoginRequestDto;
import com.omniguardy.backend.domain.auth.presentation.dto.request.SignupRequestDto;
import com.omniguardy.backend.domain.auth.presentation.dto.response.LoginResponseDto;
import com.omniguardy.backend.domain.auth.presentation.dto.response.ReissueResponseDto;
import com.omniguardy.backend.domain.auth.presentation.dto.response.SignupResponseDto;
import org.springframework.stereotype.Component;

@Component
public class AuthPresentationMapper {
    public SignupCommand toCommand(SignupRequestDto request) {
        return new SignupCommand(request.getEmail(), request.getPassword(), request.getName(), request.getPhoneNumber());
    }

    public LoginCommand toCommand(LoginRequestDto request) {
        return new LoginCommand(request.getEmail(), request.getPassword());
    }

    public SignupResponseDto toSignupResponse(AuthSession session) {
        return SignupResponseDto.builder().accessToken(session.accessToken()).tokenType("Bearer")
                .userId(session.userId()).email(session.email()).name(session.name()).build();
    }

    public LoginResponseDto toLoginResponse(AuthSession session) {
        return LoginResponseDto.builder().accessToken(session.accessToken()).tokenType("Bearer")
                .userId(session.userId()).email(session.email()).name(session.name()).build();
    }

    public ReissueResponseDto toReissueResponse(AuthSession session) {
        return ReissueResponseDto.builder().accessToken(session.accessToken()).tokenType("Bearer").build();
    }
}
