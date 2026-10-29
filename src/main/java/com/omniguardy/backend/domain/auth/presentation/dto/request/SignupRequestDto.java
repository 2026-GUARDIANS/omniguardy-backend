package com.omniguardy.backend.domain.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class SignupRequestDto {

    @Schema(description = "로그인에 사용할 이메일", example = "guard@example.com")
    @Email(message = "?щ컮瑜??대찓???뺤떇???꾨떃?덈떎.")
    @NotBlank(message = "?대찓?쇱? ?꾩닔?낅땲??")
    private String email;

    @Schema(description = "계정 비밀번호", example = "Guard!2345")
    @NotBlank(message = "鍮꾨?踰덊샇???꾩닔?낅땲??")
    private String password;

    @Schema(description = "사용자 이름", example = "김가디")
    @NotBlank(message = "?대쫫? ?꾩닔?낅땲??")
    private String name;

    @Schema(description = "연락처", example = "010-1234-5678", nullable = true)
    private String phoneNumber;
}

