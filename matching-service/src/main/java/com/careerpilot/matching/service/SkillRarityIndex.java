package com.careerpilot.matching.service;

import com.careerpilot.matching.client.JobServiceClient;
import com.careerpilot.matching.client.JobServiceClient.JobSummary;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * How rare each skill is across the live job corpus, so skill overlap can be weighted by
 * what a match actually signals.
 *
 * <p>This is the difference between a fit score that means something and one that does
 * not. Matching on "Git" says almost nothing -- nearly every posting mentions it. Matching
 * on "Kubernetes" is a real signal. Plain overlap counting treats them identically;
 * IDF weighting does not:
 * {@code idf(skill) = ln((N + 1) / (df(skill) + 1)) + 1}, computed over the same corpus
 * the jobs themselves come from, so rarity is measured against this market rather than
 * assumed.
 *
 * <p>Note this measures rarity among <em>job postings</em> (demand side), not among
 * candidates. A skill few employers ask for scores high here, which is the intended
 * reading: matching a specialised requirement is more distinguishing than matching a
 * ubiquitous one.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillRarityIndex {

    private static final int SAMPLE_SIZE = 300;

    private final JobServiceClient jobServiceClient;
    private final JobSkillExtractor skillExtractor;

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
     * <p>Needed because {@code @PostConstruct} can run before job-service has finished
     * registering with Eureka: compose's {@code depends_on: service_healthy} waits for the
     * healthcheck, not for service discovery, so a cold start of the whole stack reliably
     * loses the first attempt. Without this the index would sit empty until the next
     * 6-hourly tick and every fit score in that window would silently fall back to
     * unweighted overlap -- wrong answers, no error.
     */
    @Scheduled(fixedDelay = 90, initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void retryUntilLoaded() {
        if (isLoaded()) {
            return;
        }
        refresh();
    }

    /** Every 6 hours, matching job-service's own free-board ingestion cadence. */
    @Scheduled(fixedDelay = 6, initialDelay = 6, timeUnit = TimeUnit.HOURS)
    public void refresh() {
        if (!skillExtractor.isLoaded()) {
            log.warn("Skill extractor not loaded yet; deferring rarity index build");
            return;
        }

        List<JobSummary> jobs = jobServiceClient.fetchCandidates("ALL", null, null, SAMPLE_SIZE);
        if (jobs.isEmpty()) {
            log.warn("Job sample empty; keeping previous rarity snapshot ({} docs)",
                    snapshot.get().corpusSize());
            return;
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (JobSummary job : jobs) {
            Set<String> slugs = skillExtractor.extractSkillSlugs(job);
            for (String slug : slugs) {
                documentFrequency.merge(slug, 1, Integer::sum);
            }
        }

        snapshot.set(new Snapshot(jobs.size(), documentFrequency));
        log.info("Skill rarity index refreshed: {} jobs, {} distinct skills observed",
                jobs.size(), documentFrequency.size());
    }

    /**
     * @return the rarity weight for a skill. An unobserved skill is treated as maximally
     * rare for the current corpus size, which is the honest reading: the sample has never
     * seen an employer ask for it, so a candidate having it is at least as distinguishing
     * as the rarest thing observed.
     */
    public double weight(String skillSlug) {
        Snapshot current = snapshot.get();
        int n = current.corpusSize();
        if (n == 0) {
            return 1.0;   // index not built yet: fall back to unweighted overlap
        }
        int df = current.documentFrequency().getOrDefault(skillSlug, 0);
        return Math.log((n + 1.0) / (df + 1.0)) + 1.0;
    }

    public boolean isLoaded() {
        return snapshot.get().corpusSize() > 0;
    }
}
