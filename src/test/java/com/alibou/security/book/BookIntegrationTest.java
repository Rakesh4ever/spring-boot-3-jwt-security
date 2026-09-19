package com.alibou.security.book;

import com.alibou.security.support.AbstractIntegrationTest;
import com.alibou.security.user.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BookIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("authenticated user can create and list books")
    void createAndListBooks() throws Exception {
        String token = registerAndGetAccessToken("books@mail.com", Role.USER);

        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "author": "Alibou",
                                  "isbn": "12345"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.author").value("Alibou"))
                .andExpect(jsonPath("$.createdBy").isNumber());

        mockMvc.perform(get("/api/v1/books")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isbn").value("12345"));
    }

    @Test
    @DisplayName("updating a book keeps createdBy and createDate")
    void updateBookPreservesAudit() throws Exception {
        String token = registerAndGetAccessToken("books-update@mail.com", Role.USER);

        var create = mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "author": "Alibou",
                                  "isbn": "12345"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(create.getResponse().getContentAsByteArray());
        int id = created.path("id").asInt();
        int createdBy = created.path("createdBy").asInt();
        String createDate = created.path("createDate").asString();

        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": %d,
                                  "author": "Alibou 2",
                                  "isbn": "12345"
                                }
                                """.formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("Alibou 2"))
                .andExpect(jsonPath("$.createdBy").value(createdBy))
                .andExpect(jsonPath("$.createDate").value(createDate));
    }

    @Test
    @DisplayName("updating a missing book returns 404")
    void updateMissingBook() throws Exception {
        String token = registerAndGetAccessToken("books-missing@mail.com", Role.USER);

        mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": 99,
                                  "author": "Alibou",
                                  "isbn": "12345"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("change password revokes the previous access token")
    void changePassword() throws Exception {
        String token = registerAndGetAccessToken("pwd@mail.com", Role.USER);

        mockMvc.perform(patch("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password",
                                  "newPassword": "newPassword",
                                  "confirmationPassword": "newPassword"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/demo-controller")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "pwd@mail.com",
                                  "password": "newPassword"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }
}
