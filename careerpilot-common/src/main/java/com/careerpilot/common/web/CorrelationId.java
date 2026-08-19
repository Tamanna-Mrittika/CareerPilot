package com.careerpilot.common.web;

/**
 * Correlation-ID vocabulary shared by every service and the gateway.
 *
 * <p>The gateway mints the ID at the edge; every downstream hop echoes it. Because it also
 * lands in the SLF4J {@link org.slf4j.MDC}, a single grep across all container logs
 * reconstructs one user request end to end -- which is the cheap, always-available
 * complement to distributed tracing in Zipkin.
 */
public final class CorrelationId {

    /** Inbound/outbound HTTP header carrying the ID across service boundaries. */
    public static final String HEADER = "X-Correlation-Id";

    /** Key under which the ID is published to the logging MDC (see logback pattern). */
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }
}
