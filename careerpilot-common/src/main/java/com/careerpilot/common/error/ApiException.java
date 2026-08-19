package com.careerpilot.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for errors that carry a deliberate HTTP semantic.
 *
 * <p>Services throw these instead of letting arbitrary runtime exceptions escape, so the
 * {@link GlobalExceptionHandler} can render every failure as RFC 7807 {@code
 * application/problem+json}. Anything not derived from this class is treated as a genuine
 * bug and rendered as an opaque 500 -- internal messages are never leaked to clients.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
