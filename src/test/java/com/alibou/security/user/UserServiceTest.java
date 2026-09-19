package com.alibou.security.user;

import com.alibou.security.exception.InvalidRequestException;
import com.alibou.security.token.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserRepository repository;
    @Mock
    private TokenService tokenService;

    @InjectMocks
    private UserService userService;

    private User user;
    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1)
                .email("ali@mail.com")
                .password("encoded-old")
                .role(Role.USER)
                .build();
        principal = new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    @Test
    @DisplayName("change password encodes and persists the new value")
    void changePasswordSuccess() {
        var request = new ChangePasswordRequest("old", "newPassword", "newPassword");
        when(passwordEncoder.matches("old", "encoded-old")).thenReturn(true);
        when(passwordEncoder.matches("newPassword", "encoded-old")).thenReturn(false);
        when(passwordEncoder.encode("newPassword")).thenReturn("encoded-new");

        userService.changePassword(request, principal);

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(repository).save(user);
        verify(tokenService).revokeAllUserTokens(user);
    }

    @Test
    @DisplayName("change password rejects a wrong current password")
    void wrongCurrentPassword() {
        var request = new ChangePasswordRequest("nope", "newPassword", "newPassword");
        when(passwordEncoder.matches("nope", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(request, principal))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Current password");
        verify(tokenService, never()).revokeAllUserTokens(user);
    }

    @Test
    @DisplayName("change password rejects mismatched confirmation")
    void confirmationMismatch() {
        var request = new ChangePasswordRequest("old", "newPassword", "other");
        when(passwordEncoder.matches("old", "encoded-old")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(request, principal))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("confirmation");
    }

    @Test
    @DisplayName("change password rejects reusing the current password")
    void sameAsCurrentPassword() {
        var request = new ChangePasswordRequest("old", "password", "password");
        when(passwordEncoder.matches("old", "encoded-old")).thenReturn(true);
        when(passwordEncoder.matches("password", "encoded-old")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(request, principal))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("different");
    }
}
