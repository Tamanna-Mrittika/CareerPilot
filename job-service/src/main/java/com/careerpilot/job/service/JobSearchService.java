package com.careerpilot.job.service;

import com.careerpilot.common.error.NotFoundException;
import com.careerpilot.job.api.dto.JobDtos.JobPage;
import com.careerpilot.job.api.dto.JobDtos.JobResponse;
import com.careerpilot.job.api.dto.JobDtos.JobSearchCriteria;
import com.careerpilot.job.domain.Job;
import com.careerpilot.job.domain.JobScope;
import com.careerpilot.job.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final JobRepository jobs;

    /**
     * Full-text search with filters.
     *
     * <p>Cached in Redis because the corpus only changes when scheduled ingestion runs
     * (every few hours), so serving repeated searches from cache costs nothing in freshness.
     * The cache is cleared explicitly at the end of each ingestion rather than relying on
     * TTL alone, so new postings appear immediately rather than after an arbitrary delay.
     *
     * <p>The key includes every filter: omitting one would let a filtered search return
     * another search's results, which is the classic cache-key bug.
     */
    @Cacheable(cacheNames = "jobSearch",
            key = "T(java.util.Objects).hash(#criteria, #pageable.pageNumber, #pageable.pageSize)")
    @Transactional(readOnly = true)
    public JobPage search(JobSearchCriteria criteria, Pageable pageable) {
        Page<Job> page = jobs.search(
                blankToNull(criteria.query()),
                (criteria.scope() == null ? JobScope.ALL : criteria.scope()).name(),
                blankToNull(criteria.city()),
                criteria.source() == null ? null : criteria.source().name(),
                criteria.employmentType() == null ? null : criteria.employmentType().name(),
                criteria.minSalary(),
                blankToNull(criteria.country()),
                pageable);

        // Mapped inside the transaction so the lazy tag collection is initialised here
        // rather than blowing up in the serializer with open-in-view disabled.
        List<JobResponse> content = page.getContent().stream().map(JobResponse::from).toList();
        return JobPage.of(content, pageable.getPageNumber(), pageable.getPageSize(),
                page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID id) {
        return jobs.findById(id).map(JobResponse::from)
                .orElseThrow(() -> NotFoundException.of("Job", id));
    }

    /**
     * Other listings of the same vacancy, found by content hash.
     *
     * <p>Surfacing these is more useful than hiding them: a candidate can see the role is
     * on three boards and pick whichever they would rather apply through.
     */
    @Transactional(readOnly = true)
    public List<JobResponse> duplicatesOf(UUID id) {
        Job job = jobs.findById(id).orElseThrow(() -> NotFoundException.of("Job", id));
        return jobs.findByContentHash(job.getContentHash()).stream()
                .filter(other -> !other.getId().equals(id))
                .map(JobResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobResponse> byCompany(String company) {
        return jobs.findByCompanyFuzzy(company).stream().map(JobResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long totalJobs() {
        return jobs.count();
    }

    @Transactional(readOnly = true)
    public long distinctVacancies() {
        return jobs.countDistinctContentHashes();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
