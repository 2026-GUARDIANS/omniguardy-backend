package com.omniguardy.backend.domain.auth.application.model;

public record SignupCommand(String email, String password, String name, String phoneNumber) {
}
