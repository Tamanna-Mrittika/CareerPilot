package com.careerpilot.job.provider;

import com.careerpilot.job.domain.EmploymentType;
import com.careerpilot.job.domain.JobSource;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Provider-neutral intermediate form.
 *
 * <p>Each adapter's job is to turn its own wire format into this; everything downstream
 * (normalisation, dedup, persistence) then works on one shape. Adding a fifth provider
 * touches exactly one new class and nothing else.
 */
@Builder
public record RawJob(
        JobSource source,
        String externalId,
        String title,
        String company,
        String locationRaw,
        String locationCity,
        String locationCountry,
        boolean remote,
        EmploymentType employmentType,
        /** May contain provider HTML; the normaliser strips it. */
        String description,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        String salaryRaw,
        String applyUrl,
        Set<String> tags,
        Instant postedAt) {
}
