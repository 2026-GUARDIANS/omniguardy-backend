package com.omniguardy.backend.domain.auth.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class LoginRequestDto {

    @Email(message = "?щ컮瑜??대찓???뺤떇???꾨떃?덈떎.")
    @NotBlank(message = "?대찓?쇱? ?꾩닔?낅땲??")
    private String email;

    @NotBlank(message = "鍮꾨?踰덊샇???꾩닔?낅땲??")
    private String password;
}

