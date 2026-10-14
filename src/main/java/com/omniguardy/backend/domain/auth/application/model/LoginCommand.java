package com.omniguardy.backend.domain.auth.application.model;

public record LoginCommand(String email, String password) {
}
