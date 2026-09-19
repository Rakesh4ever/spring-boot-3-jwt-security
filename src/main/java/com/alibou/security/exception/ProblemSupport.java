package com.alibou.security.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

final class ProblemSupport {

    private ProblemSupport() {
    }

    static ProblemDetail problem(HttpStatus status, String title, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.security.local/errors/" + code.toLowerCase()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        if (request != null) {
            problem.setProperty("path", request.getRequestURI());
        }
        return problem;
    }
}
