package com.alibou.security.config;

import com.alibou.security.token.Token;
import com.alibou.security.token.TokenService;
import com.alibou.security.token.TokenType;
import com.alibou.security.user.Role;
import com.alibou.security.user.User;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private TokenService tokenService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        user = User.builder()
                .id(1)
                .email("ali@mail.com")
                .password("encoded")
                .role(Role.USER)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("auth paths are skipped")
    void skipsAuthPaths() throws Exception {
        when(request.getServletPath()).thenReturn("/api/v1/auth/register");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("a valid stored access token populates the security context")
    void authenticatesValidToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtService.extractUsername("good-token")).thenReturn("ali@mail.com");
        when(userDetailsService.loadUserByUsername("ali@mail.com")).thenReturn(user);
        when(tokenService.findActive("good-token", TokenType.ACCESS))
                .thenReturn(Optional.of(Token.builder().token("good-token").tokenType(TokenType.ACCESS).build()));
        when(jwtService.isTokenValid("good-token", user)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ali@mail.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("a revoked or missing access token does not authenticate")
    void revokedToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer revoked");
        when(jwtService.extractUsername("revoked")).thenReturn("ali@mail.com");
        when(userDetailsService.loadUserByUsername("ali@mail.com")).thenReturn(user);
        when(tokenService.findActive("revoked", TokenType.ACCESS)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid("revoked", user);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("a refresh token cannot be used as an access token")
    void refreshTokenRejectedAsAccess() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer refresh");
        when(jwtService.extractUsername("refresh")).thenReturn("ali@mail.com");
        when(userDetailsService.loadUserByUsername("ali@mail.com")).thenReturn(user);
        when(tokenService.findActive("refresh", TokenType.ACCESS)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).isTokenValid("refresh", user);
    }

    @Test
    @DisplayName("an expired JWT does not abort the filter chain")
    void expiredJwtIsIgnored() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired");
        when(jwtService.extractUsername("expired"))
                .thenThrow(new MalformedJwtException("expired"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
