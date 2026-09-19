package com.alibou.security.auth;

import com.alibou.security.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "firstname is required")
        String firstname,
        @NotBlank(message = "lastname is required")
        String lastname,
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,
        @NotNull(message = "role is required")
        Role role
) {
}
