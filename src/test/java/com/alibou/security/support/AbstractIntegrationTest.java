package com.alibou.security.support;

import com.alibou.security.user.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String registerAndGetAccessToken(String email, Role role) throws Exception {
        return register(email, role).path("access_token").asString();
    }

    protected String registerAndGetRefreshToken(String email, Role role) throws Exception {
        return register(email, role).path("refresh_token").asString();
    }

    protected JsonNode register(String email, Role role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "Test",
                                  "lastname": "User",
                                  "email": "%s",
                                  "password": "password",
                                  "role": "%s"
                                }
                                """.formatted(email, role.name())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
