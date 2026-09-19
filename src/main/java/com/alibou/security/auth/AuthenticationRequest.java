package com.alibou.security.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticationRequest(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,
        @NotBlank(message = "password is required")
        @Size(max = 72, message = "password must be at most 72 characters")
        String password
) {
}
