package com.careerpilot.job.provider;

import com.careerpilot.job.domain.JobSource;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * One external job board.
 *
 * <p>The Strategy pattern here is what lets the system degrade gracefully: providers are
 * discovered as Spring beans, each is wrapped independently in its own circuit breaker, and
 * one board being down or rate-limiting never blocks ingestion from the others.
 */
public interface JobProvider {

    JobSource source();

    /**
     * Whether this provider can run. Adzuna returns false when no API key is configured,
     * so a fresh clone with no credentials still ingests from the three keyless boards
     * instead of failing.
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * How long the ingestion service should wait for this provider before giving up.
     *
     * <p>Defaults to the shared provider timeout. Apify overrides this: an actor run
     * takes minutes, not seconds, and the earlier bug here was exactly this default being
     * applied uniformly -- it silently discarded a run that had already completed and
     * been billed on Apify's side, because the client gave up waiting for the response.
     */
    default Duration fetchTimeout(ProviderProperties properties) {
        return properties.timeout();
    }

    /**
     * Fetches the current batch of postings.
     *
     * <p>Implementations must not throw: a failing provider signals with {@code Flux.error}
     * so the caller can apply resilience policy uniformly.
     */
    Flux<RawJob> fetch();
}
