package com.careerpilot.job.api.dto;

import com.careerpilot.job.domain.EmploymentType;
import com.careerpilot.job.domain.Job;
import com.careerpilot.job.domain.JobScope;
import com.careerpilot.job.domain.JobSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobDtos {

    private JobDtos() {
    }

    /**
     * Search filters. A record so it has value-based equals/hashCode, which is what makes
     * it safe to use as part of the Redis cache key.
     */
    public record JobSearchCriteria(
            String query,
            JobScope scope,
            String city,
            JobSource source,
            EmploymentType employmentType,
            BigDecimal minSalary,
            String country) {
    }

    public record JobResponse(
            UUID id,
            String title,
            String company,
            String location,
            String country,
            boolean remote,
            EmploymentType employmentType,
            String description,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String salaryRaw,
            String applyUrl,
            List<String> tags,
            String city,
            Instant postedAt,
            /** Required by Remotive's and RemoteOK's API terms -- never drop this in the UI. */
            String source,
            String sourceAttribution) {

        public static JobResponse from(Job job) {
            return new JobResponse(
                    job.getId(),
                    job.getTitle(),
                    job.getCompany(),
                    job.getLocationRaw(),
                    job.getLocationCountry(),
                    job.isRemote(),
                    job.getEmploymentType(),
                    job.getDescription(),
                    job.getSalaryMin(),
                    job.getSalaryMax(),
                    job.getSalaryCurrency(),
                    job.getSalaryRaw(),
                    job.getApplyUrl(),
                    List.copyOf(job.getTags()),
                    job.getLocationCity(),
                    job.getPostedAt(),
                    job.getSource().name(),
                    job.getSource().attribution());
        }
    }

    /**
     * Explicit page envelope, used instead of Spring Data's {@code Page}.
     *
     * <p>{@code PageImpl} has no default constructor and is documented as unsupported for
     * serialization, so a cached {@code Page} writes to Redis fine and then fails on the
     * first cache <em>hit</em> -- a bug that only appears on the second identical request.
     * A plain record round-trips cleanly and pins the JSON contract the frontend sees.
     */
    public record JobPage(
            List<JobResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last) {

        public static JobPage of(List<JobResponse> content, int page, int size, long totalElements) {
            int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
            return new JobPage(content, page, size, totalElements, totalPages, page + 1 >= totalPages);
        }
    }

    public record IngestionStatsResponse(
            long totalListings,
            /** Distinct vacancies after collapsing cross-board duplicates. */
            long distinctVacancies,
            java.util.Map<String, Long> countsBySource,
            java.util.Map<String, String> circuitBreakers) {
    }
}
