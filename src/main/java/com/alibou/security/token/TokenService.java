package com.alibou.security.token;

import com.alibou.security.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepository;

    @Transactional
    public void persist(User user, String jwt, TokenType type) {
        tokenRepository.save(Token.builder()
                .user(user)
                .token(jwt)
                .tokenType(type)
                .expired(false)
                .revoked(false)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<Token> findActive(String jwt) {
        return tokenRepository.findByToken(jwt)
                .filter(token -> !token.isExpired() && !token.isRevoked());
    }

    @Transactional(readOnly = true)
    public Optional<Token> findActive(String jwt, TokenType type) {
        return findActive(jwt).filter(token -> token.getTokenType() == type);
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        var tokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (tokens.isEmpty()) {
            return;
        }
        tokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(tokens);
    }
}
