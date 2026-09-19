package com.alibou.security.exception;

import org.springframework.http.HttpStatus;

public sealed class ApiException extends RuntimeException
    permits DuplicateResourceException, ResourceNotFoundException, InvalidRequestException, InvalidTokenException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
