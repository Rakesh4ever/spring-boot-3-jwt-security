package com.alibou.security.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("duplicate resource is mapped to 409 problem+json")
    void duplicateResource() throws Exception {
        mockMvc.perform(get("/stub/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")))
                .andExpect(jsonPath("$.path").value("/stub/duplicate"));
    }

    @Test
    @DisplayName("bean validation failures are mapped to 400 with field errors")
    void validationFailed() throws Exception {
        mockMvc.perform(post("/stub/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("bad credentials are mapped to 401 without leaking internals")
    void badCredentials() throws Exception {
        mockMvc.perform(get("/stub/credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    @DisplayName("unique-constraint races are mapped to 409")
    void dataIntegrity() throws Exception {
        mockMvc.perform(get("/stub/constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("unexpected errors are mapped to 500 with a generic detail")
    void unexpected() throws Exception {
        mockMvc.perform(get("/stub/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @RestController
    static class StubController {

        @GetMapping("/stub/duplicate")
        String duplicate() {
            throw new DuplicateResourceException("User", "ali@mail.com");
        }

        @PostMapping("/stub/validate")
        String validate(@Valid @RequestBody Payload payload) {
            return payload.name();
        }

        @GetMapping("/stub/credentials")
        String credentials() {
            throw new BadCredentialsException("secret-reason");
        }

        @GetMapping("/stub/constraint")
        String constraint() {
            throw new DataIntegrityViolationException("unique");
        }

        @GetMapping("/stub/boom")
        String boom() {
            throw new IllegalStateException("explode");
        }

        record Payload(@NotBlank(message = "name is required") String name) {
        }
    }
}
