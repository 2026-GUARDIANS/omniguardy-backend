package com.omniguardy.backend.global.success;

import com.omniguardy.backend.domain.auth.presentation.success.AuthSuccessCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class SuccessResponseTest {
    @Test
    void usesStatusAndBusinessCodeFromSuccessCode() {
        var response = SuccessResponse.of(AuthSuccessCode.SIGNUP_SUCCESS, "created-user");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("SIGNUP_SUCCESS", response.getBody().getCode());
        assertEquals("created-user", response.getBody().getData());
    }
}
