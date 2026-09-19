package com.alibou.security.exception;

import org.springframework.http.HttpStatus;

public final class InvalidTokenException extends ApiException {

    public InvalidTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", message);
    }
}
