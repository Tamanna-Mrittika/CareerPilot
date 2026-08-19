package com.careerpilot.common.error;

import org.springframework.http.HttpStatus;

/** Semantically invalid input that bean validation cannot express (bad file type, unparseable PDF). */
public class BadRequestException extends ApiException {

    public BadRequestException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }
}
