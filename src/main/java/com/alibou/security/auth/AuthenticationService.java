package com.alibou.security.auth;

import com.alibou.security.config.JwtService;
import com.alibou.security.exception.DuplicateResourceException;
import com.alibou.security.exception.InvalidTokenException;
import com.alibou.security.exception.ResourceNotFoundException;
import com.alibou.security.token.TokenService;
import com.alibou.security.token.TokenType;
import com.alibou.security.user.User;
import com.alibou.security.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", request.email());
        }
        var user = User.builder()
                .firstname(request.firstname())
                .lastname(request.lastname())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        var savedUser = userRepository.save(user);
        return issueTokens(savedUser, false);
    }

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.email()));
        return issueTokens(user, true);
    }

    @Transactional
    public AuthenticationResponse refreshToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Refresh token is missing");
        }
        String refreshToken = authHeader.substring(7);
        var stored = tokenService.findActive(refreshToken, TokenType.REFRESH)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is expired or invalid"));
        var user = stored.getUser();
        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new InvalidTokenException("Refresh token is expired or invalid");
        }
        return issueTokens(user, true);
    }

    private AuthenticationResponse issueTokens(User user, boolean revokeExisting) {
        var accessToken = jwtService.generateToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        if (revokeExisting) {
            tokenService.revokeAllUserTokens(user);
        }
        tokenService.persist(user, accessToken, TokenType.ACCESS);
        tokenService.persist(user, refreshToken, TokenType.REFRESH);
        return new AuthenticationResponse(accessToken, refreshToken);
    }
}
