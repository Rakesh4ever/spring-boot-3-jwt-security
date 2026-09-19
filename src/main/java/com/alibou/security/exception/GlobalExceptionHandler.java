package com.alibou.security.exception;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        return ProblemSupport.problem(ex.status(), ex.status().getReasonPhrase(), ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemSupport.problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "VALIDATION_FAILED",
                "Request body is invalid",
                request
        );
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        ProblemDetail problem = ProblemSupport.problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "VALIDATION_FAILED",
                "Request parameters are invalid",
                request
        );
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ProblemDetail handleBadCredentials(RuntimeException ex, HttpServletRequest request) {
        return ProblemSupport.problem(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "BAD_CREDENTIALS",
                "Invalid email or password",
                request
        );
    }

    @ExceptionHandler({AuthenticationException.class, JwtException.class})
    public ProblemDetail handleAuthentication(RuntimeException ex, HttpServletRequest request) {
        return ProblemSupport.problem(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "UNAUTHORIZED",
                "Authentication is required or the token is invalid",
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ProblemSupport.problem(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                request
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return ProblemSupport.problem(
                HttpStatus.CONFLICT,
                "Conflict",
                "DUPLICATE_RESOURCE",
                "Resource already exists",
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ProblemSupport.problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "MALFORMED_REQUEST",
                "Request body is missing or could not be read",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return ProblemSupport.problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request
        );
    }
}
