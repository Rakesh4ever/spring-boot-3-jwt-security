package com.alibou.security.auth;

import com.alibou.security.config.JwtService;
import com.alibou.security.exception.DuplicateResourceException;
import com.alibou.security.exception.InvalidTokenException;
import com.alibou.security.token.Token;
import com.alibou.security.token.TokenService;
import com.alibou.security.token.TokenType;
import com.alibou.security.user.Role;
import com.alibou.security.user.User;
import com.alibou.security.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthenticationService authenticationService;

    private RegisterRequest registerRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("Ali", "Bouali", "ali@mail.com", "password", Role.ADMIN);
        savedUser = User.builder()
                .id(1)
                .firstname("Ali")
                .lastname("Bouali")
                .email("ali@mail.com")
                .password("encoded")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    @DisplayName("register encodes the password, stores the user and persists both tokens")
    void registerSuccess() {
        when(userRepository.existsByEmail("ali@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(savedUser)).thenReturn("access");
        when(jwtService.generateRefreshToken(savedUser)).thenReturn("refresh");

        AuthenticationResponse response = authenticationService.register(registerRequest);

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded");
        verify(tokenService, never()).revokeAllUserTokens(any());
        verify(tokenService).persist(savedUser, "access", TokenType.ACCESS);
        verify(tokenService).persist(savedUser, "refresh", TokenType.REFRESH);
    }

    @Test
    @DisplayName("register rejects a duplicate email")
    void registerDuplicateEmail() {
        when(userRepository.existsByEmail("ali@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.register(registerRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ali@mail.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("authenticate revokes previous tokens and issues a new pair")
    void authenticateSuccess() {
        var request = new AuthenticationRequest("ali@mail.com", "password");
        when(userRepository.findByEmail("ali@mail.com")).thenReturn(Optional.of(savedUser));
        when(jwtService.generateToken(savedUser)).thenReturn("access");
        when(jwtService.generateRefreshToken(savedUser)).thenReturn("refresh");

        AuthenticationResponse response = authenticationService.authenticate(request);

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(authenticationManager).authenticate(any());
        InOrder order = inOrder(tokenService);
        order.verify(tokenService).revokeAllUserTokens(savedUser);
        order.verify(tokenService).persist(savedUser, "access", TokenType.ACCESS);
        order.verify(tokenService).persist(savedUser, "refresh", TokenType.REFRESH);
    }

    @Test
    @DisplayName("authenticate surfaces bad credentials")
    void authenticateBadCredentials() {
        var request = new AuthenticationRequest("ali@mail.com", "wrong");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authenticationService.authenticate(request))
                .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("refresh token rotates access and refresh after a stored refresh lookup")
    void refreshTokenSuccess() {
        Token stored = Token.builder()
                .token("refresh-token")
                .tokenType(TokenType.REFRESH)
                .user(savedUser)
                .expired(false)
                .revoked(false)
                .build();
        when(httpRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer refresh-token");
        when(tokenService.findActive("refresh-token", TokenType.REFRESH)).thenReturn(Optional.of(stored));
        when(jwtService.isTokenValid("refresh-token", savedUser)).thenReturn(true);
        when(jwtService.generateToken(savedUser)).thenReturn("new-access");
        when(jwtService.generateRefreshToken(savedUser)).thenReturn("new-refresh");

        AuthenticationResponse response = authenticationService.refreshToken(httpRequest);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(tokenService).revokeAllUserTokens(savedUser);
        verify(tokenService).persist(savedUser, "new-access", TokenType.ACCESS);
        verify(tokenService).persist(savedUser, "new-refresh", TokenType.REFRESH);
    }

    @Test
    @DisplayName("refresh token without a Bearer header is rejected")
    void refreshTokenMissingHeader() {
        when(httpRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        assertThatThrownBy(() -> authenticationService.refreshToken(httpRequest))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("refresh token that is not in the store is rejected")
    void refreshTokenNotStored() {
        when(httpRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer refresh-token");
        when(tokenService.findActive("refresh-token", TokenType.REFRESH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.refreshToken(httpRequest))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired or invalid");
        verify(jwtService, never()).isTokenValid(any(), any());
    }

    @Test
    @DisplayName("expired stored refresh token is rejected as INVALID_TOKEN")
    void refreshTokenExpiredJwt() {
        Token stored = Token.builder()
                .token("refresh-token")
                .tokenType(TokenType.REFRESH)
                .user(savedUser)
                .build();
        when(httpRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer refresh-token");
        when(tokenService.findActive("refresh-token", TokenType.REFRESH)).thenReturn(Optional.of(stored));
        when(jwtService.isTokenValid("refresh-token", savedUser)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.refreshToken(httpRequest))
                .isInstanceOf(InvalidTokenException.class);
    }
}
