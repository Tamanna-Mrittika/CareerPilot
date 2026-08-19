package com.careerpilot.common.error;

import org.springframework.http.HttpStatus;

/** Request is well formed but conflicts with current state (duplicate email, illegal Kanban transition). */
public class ConflictException extends ApiException {

    public ConflictException(String detail) {
        super(HttpStatus.CONFLICT, "Conflict", detail);
    }
}
