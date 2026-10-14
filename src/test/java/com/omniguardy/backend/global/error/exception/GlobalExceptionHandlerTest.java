package com.omniguardy.backend.global.error.exception;

import com.omniguardy.backend.domain.auth.domain.error.AuthErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void usesStatusAndBusinessCodeFromErrorCode() {
        var response = handler.handleBusinessException(new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("EMAIL_ALREADY_EXISTS", response.getBody().code());
    }
}
