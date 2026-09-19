package com.alibou.security.bootstrap;

import com.alibou.security.auth.AuthenticationService;
import com.alibou.security.auth.RegisterRequest;
import com.alibou.security.user.Role;
import com.alibou.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DemoDataLoader implements CommandLineRunner {

    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        registerIfAbsent("admin@mail.com", Role.ADMIN);
        registerIfAbsent("manager@mail.com", Role.MANAGER);
    }

    private void registerIfAbsent(String email, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        var response = authenticationService.register(new RegisterRequest(
                "Demo",
                role.name(),
                email,
                "password",
                role
        ));
        log.info("{} token: {}", role, response.accessToken());
    }
}
