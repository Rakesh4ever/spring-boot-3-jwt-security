package com.alibou.security.auth;

import com.alibou.security.support.AbstractIntegrationTest;
import com.alibou.security.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("register returns access and refresh tokens")
    void register() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "Ali",
                                  "lastname": "Bouali",
                                  "email": "ali@mail.com",
                                  "password": "password",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty());
    }

    @Test
    @DisplayName("duplicate email returns 409")
    void duplicateEmail() throws Exception {
        register("dup@mail.com", Role.USER);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "Ali",
                                  "lastname": "Bouali",
                                  "email": "dup@mail.com",
                                  "password": "password",
                                  "role": "USER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("invalid register payload returns 400 with field errors")
    void invalidRegisterPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "",
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("login returns tokens")
    void authenticate() throws Exception {
        register("login@mail.com", Role.USER);

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "login@mail.com",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("wrong password returns 401")
    void authenticateWrongPassword() throws Exception {
        register("wrong@mail.com", Role.USER);

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "wrong@mail.com",
                                  "password": "bad-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @DisplayName("oversized login password stays in the validation model")
    void authenticatePasswordTooLong() throws Exception {
        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "any@mail.com",
                                  "password": "%s"
                                }
                                """.formatted("p".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("access token can call a secured endpoint; missing token is 401")
    void securedEndpoint() throws Exception {
        String token = registerAndGetAccessToken("secured@mail.com", Role.USER);

        mockMvc.perform(get("/api/v1/demo-controller"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/demo-controller")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refresh token cannot be used as an access token")
    void refreshTokenRejectedOnSecuredEndpoint() throws Exception {
        String refresh = registerAndGetRefreshToken("refresh-as-access@mail.com", Role.USER);

        mockMvc.perform(get("/api/v1/demo-controller")
                        .header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh rotates tokens and the previous refresh is rejected")
    void refreshToken() throws Exception {
        JsonNode tokens = register("refresh@mail.com", Role.USER);
        String refreshToken = tokens.path("refresh_token").asString();

        var refreshResult = mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        JsonNode rotated = objectMapper.readTree(refreshResult.getResponse().getContentAsByteArray());
        String newAccess = rotated.path("access_token").asString();

        mockMvc.perform(get("/api/v1/demo-controller")
                        .header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("an access token cannot be used as a refresh token")
    void accessTokenRejectedAsRefresh() throws Exception {
        String access = registerAndGetAccessToken("access-as-refresh@mail.com", Role.USER);

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("logout revokes access and refresh tokens")
    void logoutRevokesToken() throws Exception {
        JsonNode tokens = register("logout@mail.com", Role.USER);
        String access = tokens.path("access_token").asString();
        String refresh = tokens.path("refresh_token").asString();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/demo-controller")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("a second login invalidates the previous refresh token")
    void loginRevokesPreviousRefresh() throws Exception {
        JsonNode first = register("relogin@mail.com", Role.USER);
        String oldRefresh = first.path("refresh_token").asString();

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "relogin@mail.com",
                                  "password": "password"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .header("Authorization", "Bearer " + oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
