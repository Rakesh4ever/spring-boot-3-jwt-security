package com.alibou.security.exception;

import org.springframework.http.HttpStatus;

public final class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String resource, String identity) {
        super(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", resource + " already exists: " + identity);
    }
}
