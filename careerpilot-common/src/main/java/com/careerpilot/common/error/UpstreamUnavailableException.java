package com.careerpilot.common.error;

import org.springframework.http.HttpStatus;

/**
 * A dependency (peer service or external job provider) is unavailable and no cached
 * fallback could satisfy the request.
 *
 * <p>Rendered as 503 rather than 500: the request was valid and retrying later is
 * meaningful. Resilience4j fallbacks throw this only after exhausting cache.
 */
public class UpstreamUnavailableException extends ApiException {

    public UpstreamUnavailableException(String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Upstream dependency unavailable", detail);
    }
}
