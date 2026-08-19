package com.careerpilot.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "Resource not found", detail);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("%s '%s' does not exist".formatted(resource, id));
    }
}
