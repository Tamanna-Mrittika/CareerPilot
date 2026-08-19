package com.careerpilot.job.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A job posting normalised into one shape regardless of which provider supplied it.
 *
 * <p>The interesting column is {@code contentHash}: the same vacancy genuinely appears on
 * several boards at once, so without cross-provider deduplication a search returns the same
 * role three times and every match score is triple-counted.
 */
@Entity
@Table(name = "job")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobSource source;

    /** The provider's own id, so re-ingesting updates rather than duplicates. */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String company;

    @Column(name = "location_raw")
    private String locationRaw;

    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "location_country")
    private String locationCountry;

    @Column(nullable = false)
    private boolean remote;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false)
    private EmploymentType employmentType = EmploymentType.OTHER;

    /** Plain text; provider HTML is stripped at ingestion so search and TF-IDF see prose. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "salary_currency", length = 8)
    private String salaryCurrency;

    /** Verbatim provider salary text, kept because parsing it is lossy. */
    @Column(name = "salary_raw")
    private String salaryRaw;

    /** Canonical URL on the provider's site -- the link-back their terms require. */
    @Column(name = "apply_url", nullable = false, length = 1000)
    private String applyUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "job_tag", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "tag", nullable = false)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "posted_at")
    private Instant postedAt;

    /**
     * SHA-256 over normalised title + company + location. Two providers listing the same
     * vacancy collide here on purpose.
     */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static Job create(UUID id, JobSource source, String externalId) {
        Job job = new Job();
        job.id = id;
        job.source = source;
        job.externalId = externalId;
        job.ingestedAt = Instant.now();
        job.updatedAt = Instant.now();
        return job;
    }
}
