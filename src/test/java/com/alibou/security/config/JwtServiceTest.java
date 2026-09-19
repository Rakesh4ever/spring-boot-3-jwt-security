package com.alibou.security.config;

import com.alibou.security.user.Role;
import com.alibou.security.user.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private JwtService jwtService;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000, 86_400_000);
        user = User.builder()
                .email("admin@mail.com")
                .password("encoded")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    @DisplayName("generated access token contains the user email as subject")
    void extractUsernameFromGeneratedToken() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin@mail.com");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("token is invalid for a different user")
    void tokenIsInvalidForDifferentUser() {
        String token = jwtService.generateToken(user);
        User other = User.builder()
                .email("other@mail.com")
                .password("encoded")
                .role(Role.USER)
                .build();

        assertThat(jwtService.isTokenValid(token, other)).isFalse();
    }

    @Test
    @DisplayName("refresh token is a valid JWT for the same user")
    void refreshTokenIsValid() {
        String refreshToken = jwtService.generateRefreshToken(user);

        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("admin@mail.com");
        assertThat(jwtService.isTokenValid(refreshToken, user)).isTrue();
    }

    @Nested
    class InvalidTokens {

        @Test
        @DisplayName("malformed token is rejected")
        void malformedToken() {
            assertThatThrownBy(() -> jwtService.extractUsername("not-a-jwt"))
                    .isInstanceOf(JwtException.class);
        }

        @Test
        @DisplayName("expired token is rejected without throwing")
        void expiredToken() {
            JwtService shortLived = new JwtService(SECRET, -1_000, -1_000);
            String token = shortLived.generateToken(user);

            assertThat(shortLived.isTokenValid(token, user)).isFalse();
        }
    }
}
