package com.alibou.security.book;

import jakarta.validation.constraints.NotBlank;

public record BookRequest(
        Integer id,
        @NotBlank(message = "author is required")
        String author,
        @NotBlank(message = "isbn is required")
        String isbn
) {
}
