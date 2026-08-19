package com.careerpilot.job.service;

import com.careerpilot.job.domain.Job;
import com.careerpilot.job.provider.RawJob;
import com.careerpilot.job.repository.JobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persists ingested postings.
 *
 * <p>This lives in its own bean rather than as a method on {@link JobIngestionService} for
 * a specific reason: Spring applies {@code @Transactional} through a proxy, and a method
 * called from another method of the <em>same</em> bean bypasses that proxy entirely. Keeping
 * it here means the annotation actually takes effect instead of silently doing nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobPersistenceService {

    private final JobRepository jobs;
    private final JobNormalizer normalizer;
    private final MeterRegistry meters;

    public record PersistResult(int created, int updated, int duplicatesLinked, int skipped) {
    }

    /**
     * Upserts by (source, externalId) so re-ingesting refreshes rather than duplicates, and
     * counts postings whose content hash already exists under a <em>different</em> source --
     * that count is the visible payoff of cross-provider deduplication.
     */
    @Transactional
    public PersistResult persist(List<RawJob> rawJobs) {
        int created = 0;
        int updated = 0;
        int duplicates = 0;
        int skipped = 0;

        Set<String> hashesSeenThisRun = new HashSet<>();

        for (RawJob raw : rawJobs) {
            if (isUnusable(raw)) {
                // Missing a title, company or apply URL makes a posting useless downstream
                // and would pollute matching; drop it rather than store a broken row.
                skipped++;
                continue;
            }

            String hash = normalizer.contentHash(raw);

            Optional<Job> existing = jobs.findBySourceAndExternalId(raw.source(), raw.externalId());
            boolean isNew = existing.isEmpty();
            Job job = existing.orElseGet(() ->
                    Job.create(UUID.randomUUID(), raw.source(), raw.externalId()));

            if (isNew && isCrossSourceDuplicate(hash, raw, hashesSeenThisRun)) {
                duplicates++;
            }

            apply(job, raw, hash);
            jobs.save(job);
            hashesSeenThisRun.add(hash);

            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        meters.counter("careerpilot.jobs.created").increment(created);
        meters.counter("careerpilot.jobs.duplicates").increment(duplicates);
        return new PersistResult(created, updated, duplicates, skipped);
    }

    private boolean isUnusable(RawJob raw) {
        return isBlank(raw.title()) || isBlank(raw.company()) || isBlank(raw.applyUrl());
    }

    private boolean isCrossSourceDuplicate(String hash, RawJob raw, Set<String> seenThisRun) {
        if (seenThisRun.contains(hash)) {
            return true;
        }
        return jobs.findByContentHash(hash).stream()
                .anyMatch(other -> other.getSource() != raw.source());
    }

    private void apply(Job job, RawJob raw, String hash) {
        job.setTitle(truncate(raw.title(), 500));
        job.setCompany(truncate(raw.company(), 300));
        job.setLocationRaw(truncate(raw.locationRaw(), 300));
        job.setLocationCity(truncate(raw.locationCity(), 120));
        job.setLocationCountry(truncate(raw.locationCountry(), 120));
        job.setRemote(raw.remote());
        job.setEmploymentType(raw.employmentType());
        job.setDescription(normalizer.toPlainText(raw.description()));
        job.setSalaryMin(raw.salaryMin());
        job.setSalaryMax(raw.salaryMax());
        job.setSalaryCurrency(truncate(raw.salaryCurrency(), 8));
        job.setSalaryRaw(truncate(raw.salaryRaw(), 255));
        job.setApplyUrl(truncate(raw.applyUrl(), 1000));
        job.setContentHash(hash);
        job.setPostedAt(raw.postedAt());
        job.setUpdatedAt(Instant.now());

        if (raw.tags() != null) {
            job.getTags().clear();
            raw.tags().stream()
                    .filter(tag -> !isBlank(tag))
                    .map(tag -> truncate(tag, 100))
                    .forEach(job.getTags()::add);
        }
    }

    /** Guards against a provider sending a field longer than its column allows. */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
