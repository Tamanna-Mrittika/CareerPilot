package com.careerpilot.job.service;

import com.careerpilot.job.domain.JobSource;
import com.careerpilot.job.provider.JobProvider;
import com.careerpilot.job.provider.ProviderProperties;
import com.careerpilot.job.provider.RawJob;
import com.careerpilot.job.repository.JobRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pulls from every enabled provider, then hands the results to
 * {@link JobPersistenceService} to normalise, deduplicate and store.
 *
 * <p>The resilience story lives here. Each provider is wrapped in its <em>own</em> circuit
 * breaker and retry, so one board being down, slow, or rate-limiting cannot stall or fail
 * the others: {@code onErrorResume} turns a dead provider into an empty stream and
 * ingestion continues with whatever the rest returned. Partial data beats no data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobIngestionService {

    private final List<JobProvider> providers;
    private final JobRepository jobs;
    private final JobPersistenceService persistence;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final MeterRegistry meters;
    private final CacheManager cacheManager;
    private final ProviderProperties properties;

    public record IngestionReport(
            Map<String, Integer> fetchedByProvider,
            Map<String, String> failedProviders,
            int created,
            int updated,
            int duplicatesLinked,
            int skipped,
            long durationMs) {
    }

    /**
     * Runs one full ingestion cycle.
     *
     * <p>The terminal {@code block()} is deliberate and safe: this is invoked from a
     * scheduler thread or an explicit admin request, never from a request-handling thread.
     * The provider calls themselves stay non-blocking and run concurrently.
     */
    public IngestionReport ingestAll() {
        return ingest(providers.stream().filter(JobProvider::isEnabled).toList());
    }

    /**
     * Ingests everything except the given sources.
     *
     * <p>Used to keep the frequent, free-board schedule from also triggering Apify: Apify
     * is billed per run (~$0.24 measured for this service's configured query, not the
     * $0.003/job the actor's listing headline implies -- LinkedIn scraping with full
     * description fetch carries real proxy and compute overhead on top of the per-result
     * fee). Sharing the 6-hourly cron would spend roughly $7/month against a $5 free
     * credit and lock the account mid-month.
     */
    public IngestionReport ingestExcluding(java.util.Set<JobSource> excluded) {
        return ingest(providers.stream()
                .filter(JobProvider::isEnabled)
                .filter(p -> !excluded.contains(p.source()))
                .toList());
    }

    /** Ingests only the given sources -- used to run Apify on its own, cost-appropriate schedule. */
    public IngestionReport ingestOnly(java.util.Set<JobSource> included) {
        return ingest(providers.stream()
                .filter(JobProvider::isEnabled)
                .filter(p -> included.contains(p.source()))
                .toList());
    }

    private IngestionReport ingest(List<JobProvider> enabled) {
        Instant started = Instant.now();
        Map<String, Integer> fetched = new LinkedHashMap<>();
        Map<String, String> failed = new LinkedHashMap<>();

        log.info("Starting ingestion from {} provider(s): {}", enabled.size(),
                enabled.stream().map(p -> p.source().name()).toList());

        // Overall wait must cover the slowest provider, not the fast common case:
        // this was the second instance of the same bug fetchTimeout() fixes on the
        // per-provider call -- a short blanket timeout here would abort the whole run
        // while Apify was still legitimately working.
        Duration overallTimeout = enabled.stream()
                .map(p -> p.fetchTimeout(properties))
                .max(Duration::compareTo)
                .orElse(properties.timeout())
                .plusSeconds(30);

        List<RawJob> collected = Flux.fromIterable(enabled)
                .flatMap(provider -> fetchResiliently(provider, fetched, failed))
                .collectList()
                .blockOptional(overallTimeout)
                .orElseGet(List::of);

        JobPersistenceService.PersistResult result = persistence.persist(collected);

        // Fresh postings invalidate cached search pages; correctness beats a warm cache.
        Optional.ofNullable(cacheManager.getCache("jobSearch")).ifPresent(cache -> cache.clear());

        long durationMs = Duration.between(started, Instant.now()).toMillis();
        log.info("Ingestion finished in {}ms: {} fetched, {} created, {} updated, "
                        + "{} cross-source duplicates, {} skipped, {} failed provider(s)",
                durationMs, collected.size(), result.created(), result.updated(),
                result.duplicatesLinked(), result.skipped(), failed.size());

        return new IngestionReport(fetched, failed, result.created(), result.updated(),
                result.duplicatesLinked(), result.skipped(), durationMs);
    }

    /**
     * Wraps one provider in its own retry, circuit breaker and timeout, then swallows any
     * failure so the overall run survives.
     *
     * <p>Order matters: retry sits inside the breaker, so several attempts count as one
     * logical call when deciding whether to trip rather than tripping it three times faster.
     */
    private Flux<RawJob> fetchResiliently(JobProvider provider,
                                          Map<String, Integer> fetched,
                                          Map<String, String> failed) {
        String name = provider.source().name().toLowerCase();
        CircuitBreaker breaker = circuitBreakers.circuitBreaker(name);
        Retry retry = retries.retry(name);
        Instant start = Instant.now();

        return provider.fetch()
                // Per-provider timeout, not the blanket default: Apify's actor genuinely
                // takes minutes, and applying a uniform short timeout here previously threw
                // away a run that had already succeeded and been billed on Apify's side.
                .timeout(provider.fetchTimeout(properties))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(breaker))
                .collectList()
                .doOnSuccess(list -> {
                    fetched.put(name, list.size());
                    meters.timer("careerpilot.provider.fetch", "provider", name)
                            .record(Duration.between(start, Instant.now()));
                    meters.counter("careerpilot.jobs.fetched", "provider", name)
                            .increment(list.size());
                    log.info("Provider {} returned {} postings", name, list.size());
                })
                .onErrorResume(error -> {
                    // A failing board must not fail the run: record it and carry on.
                    failed.put(name, error.getClass().getSimpleName() + ": " + error.getMessage());
                    fetched.put(name, 0);
                    meters.counter("careerpilot.provider.failures", "provider", name).increment();
                    log.warn("Provider {} failed ({}); continuing with the others", name, error.toString());
                    return Mono.just(List.<RawJob>of());
                })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Runs ingestion only when the corpus is empty, so a fresh deployment has searchable
     * data immediately while ordinary restarts do not spend API quota re-fetching what is
     * already stored.
     */
    public void ingestIfCorpusEmpty() {
        long existing = jobs.count();
        if (existing > 0) {
            log.info("Corpus already holds {} postings; skipping startup ingestion", existing);
            return;
        }
        log.info("Corpus is empty; running initial ingestion");
        ingestAll();
    }

    public Map<String, Long> countsBySource() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (JobSource source : JobSource.values()) {
            counts.put(source.name(), jobs.countBySource(source));
        }
        return counts;
    }

    /** Circuit-breaker states, so the demo can show a breaker opening in real time. */
    public Map<String, String> circuitBreakerStates() {
        Map<String, String> states = new LinkedHashMap<>();
        for (JobProvider provider : providers) {
            String name = provider.source().name().toLowerCase();
            states.put(name, circuitBreakers.circuitBreaker(name).getState().name());
        }
        return states;
    }
}
