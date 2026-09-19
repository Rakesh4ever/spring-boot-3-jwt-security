package com.alibou.security.demo;

import com.alibou.security.support.AbstractIntegrationTest;
import com.alibou.security.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("USER can hit demo but not management or admin")
    void userRole() throws Exception {
        String token = registerAndGetAccessToken("user@mail.com", Role.USER);

        mockMvc.perform(get("/api/v1/demo-controller").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/management").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MANAGER can hit management but not admin")
    void managerRole() throws Exception {
        String token = registerAndGetAccessToken("manager-it@mail.com", Role.MANAGER);

        mockMvc.perform(get("/api/v1/management").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/management").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN can hit both management and admin")
    void adminRole() throws Exception {
        String token = registerAndGetAccessToken("admin-it@mail.com", Role.ADMIN);

        mockMvc.perform(get("/api/v1/management").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
