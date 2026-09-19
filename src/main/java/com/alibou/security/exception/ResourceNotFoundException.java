package com.alibou.security.exception;

import org.springframework.http.HttpStatus;

public final class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, String identity) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " not found: " + identity);
    }
}
