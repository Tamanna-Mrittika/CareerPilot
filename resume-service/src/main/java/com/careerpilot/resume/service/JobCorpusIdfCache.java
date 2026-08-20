package com.careerpilot.resume.service;

import com.careerpilot.resume.client.JobServiceClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

/**
 * Maintains inverse document frequency over a sample of the live job corpus, so ATS
 * scoring can weight a job description's keywords by how <em>rare</em> they are across the
 * market -- "Kubernetes" should count for more than "team", because almost every posting
 * says "team".
 *
 * <p>This is a genuine, if small-corpus, application of classic IDF:
 * {@code idf(term) = ln((N + 1) / (df(term) + 1)) + 1} (the "+1" smoothing avoids a
 * division by zero for a term that has not been seen yet, and keeps IDF non-negative).
 * Rebuilt periodically from a sample rather than the full corpus -- job-service already
 * holds hundreds of postings and will hold more once Apify's Dhaka feed grows, and a
 * few-hundred-document sample is already enough to separate common words from rare ones;
 * scanning the entire corpus on every refresh would not materially change the weights.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobCorpusIdfCache {

    private static final int SAMPLE_SIZE = 300;

    private final JobServiceClient jobServiceClient;
    private final TextTokenizer tokenizer;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    private record Snapshot(int corpusSize, Map<String, Integer> documentFrequency) {
        static Snapshot empty() {
            return new Snapshot(0, Map.of());
        }
    }

    @PostConstruct
    void loadInitial() {
        refresh();
    }

    /**
     * Short-interval retry until the first successful load, then it stops doing work.
     *
     * <p>{@code @PostConstruct} can run before job-service has registered with Eureka --
     * compose's {@code depends_on: service_healthy} waits for the healthcheck, not for
     * service discovery, so a cold start of the whole stack reliably loses the first
     * attempt (observed: 503 from an unresolved lb:// host). Without this the corpus would
     * stay empty until the next 6-hourly tick, and every ATS score in that window would
     * silently weight all terms equally instead of by rarity.
     */
    @Scheduled(fixedDelay = 90, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void retryUntilLoaded() {
        if (isLoaded()) {
            return;
        }
        refresh();
    }

    /** Every 6 hours -- roughly matching how often job-service's own free-board ingestion runs. */
    @Scheduled(fixedDelay = 6, initialDelay = 6, timeUnit = TimeUnit.HOURS)
    public void refresh() {
        List<String> descriptions = jobServiceClient.sampleDescriptions(SAMPLE_SIZE);
        if (descriptions.isEmpty()) {
            log.warn("Job corpus sample was empty; keeping the previous IDF snapshot "
                    + "({} documents) rather than replacing it with an empty one",
                    snapshot.get().corpusSize());
            return;
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String description : descriptions) {
            Set<String> uniqueTerms = Set.copyOf(tokenizer.tokenize(description));
            for (String term : uniqueTerms) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        snapshot.set(new Snapshot(descriptions.size(), documentFrequency));
        log.info("Job corpus IDF cache refreshed: {} documents, {} distinct terms",
                descriptions.size(), documentFrequency.size());
    }

    /**
     * @return the IDF weight for a term. Unseen terms get the maximum possible weight for
     * the current corpus size (treated as maximally rare, i.e. df = 0) -- a job description
     * using a term the sampled corpus has never seen is, if anything, more distinctive, not
     * less.
     */
    public double idf(String term) {
        Snapshot current = snapshot.get();
        int n = current.corpusSize();
        if (n == 0) {
            return 1.0;
        }
        int df = current.documentFrequency().getOrDefault(term, 0);
        return Math.log((n + 1.0) / (df + 1.0)) + 1.0;
    }

    public boolean isLoaded() {
        return snapshot.get().corpusSize() > 0;
    }
}
