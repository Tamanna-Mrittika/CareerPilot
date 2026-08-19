package com.careerpilot.job.api;

import com.careerpilot.job.api.dto.JobDtos.IngestionStatsResponse;
import com.careerpilot.job.api.dto.JobDtos.JobPage;
import com.careerpilot.job.api.dto.JobDtos.JobResponse;
import com.careerpilot.job.api.dto.JobDtos.JobSearchCriteria;
import com.careerpilot.job.domain.EmploymentType;
import com.careerpilot.job.domain.JobScope;
import com.careerpilot.job.domain.JobSource;
import com.careerpilot.job.service.JobIngestionService;
import com.careerpilot.job.service.JobIngestionService.IngestionReport;
import com.careerpilot.job.service.JobSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Local and remote job discovery are separate endpoints, not one endpoint with a flag,
 * because they back separate pages in the product. They are genuinely different searches:
 * local is "employers I can commute to in my city", remote is "I am competing globally".
 * Merged into one list, a few hundred remote postings bury the handful of local ones.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs")
public class JobController {

    private final JobSearchService searchService;
    private final JobIngestionService ingestionService;

    /** Falls back to the deployment's home market when the caller supplies no location. */
    @Value("${careerpilot.search.default-city:Dhaka}")
    private String defaultCity;

    @Value("${careerpilot.search.default-country:Bangladesh}")
    private String defaultCountry;

    @GetMapping("/local")
    @Operation(summary = "On-site and hybrid jobs in a city",
            description = """
                    Defaults to the deployment's home city (Dhaka, Bangladesh) when no
                    location is given. City is matched loosely, because boards write
                    "Dhaka", "Dhaka, Bangladesh" and "Dhaka Division" for the same place.
                    """)
    public JobPage local(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) BigDecimal minSalary,
            @PageableDefault(size = 20) Pageable pageable) {
        return searchService.search(new JobSearchCriteria(
                q,
                JobScope.LOCAL,
                // Absent means "use the home market"; explicitly empty means "no filter".
                // Collapsing the two makes ?country= silently re-apply the default and
                // return nothing.
                resolve(city, defaultCity),
                null,
                employmentType,
                minSalary,
                resolve(country, defaultCountry)),
                pageable);
    }

    /** null (parameter omitted) falls back to the default; blank means "no filter". */
    private static String resolve(String supplied, String fallback) {
        if (supplied == null) {
            return fallback == null || fallback.isBlank() ? null : fallback;
        }
        return supplied.isBlank() ? null : supplied;
    }

    @GetMapping("/remote")
    @Operation(summary = "Remote jobs, location-independent",
            description = "No city filter is applied: remote roles are open regardless of where you sit.")
    public JobPage remote(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) JobSource source,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) BigDecimal minSalary,
            @PageableDefault(size = 20) Pageable pageable) {
        return searchService.search(new JobSearchCriteria(
                q, JobScope.REMOTE, null, source, employmentType, minSalary, null),
                pageable);
    }

    @GetMapping
    @Operation(summary = "Search the whole corpus (local and remote together)",
            description = """
                    General search across everything. Results carry `sourceAttribution`,
                    which the UI must display: Remotive and RemoteOK both require
                    attribution and a link back under their API terms.
                    """)
    public JobPage search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "ALL") JobScope scope,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) JobSource source,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) BigDecimal minSalary,
            @PageableDefault(size = 20) Pageable pageable) {
        return searchService.search(new JobSearchCriteria(
                q, scope, city, source, employmentType, minSalary, country), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single posting")
    public JobResponse get(@PathVariable UUID id) {
        return searchService.get(id);
    }

    @GetMapping("/{id}/duplicates")
    @Operation(summary = "Other boards listing the same vacancy",
            description = "Matched by content hash over normalised title, company and remote flag.")
    public List<JobResponse> duplicates(@PathVariable UUID id) {
        return searchService.duplicatesOf(id);
    }

    @GetMapping("/stats")
    @Operation(summary = "Corpus and resilience statistics",
            description = """
                    `totalListings` counts raw postings; `distinctVacancies` counts them
                    after collapsing cross-board duplicates -- the gap between the two is
                    what deduplication removed. Also reports each provider's
                    circuit-breaker state.
                    """)
    public IngestionStatsResponse stats() {
        return new IngestionStatsResponse(
                searchService.totalJobs(),
                searchService.distinctVacancies(),
                ingestionService.countsBySource(),
                ingestionService.circuitBreakerStates());
    }

    /**
     * Manual ingestion trigger, restricted to ADMIN.
     *
     * <p>Locked down because each call spends finite external API quota: left open, any
     * authenticated user could exhaust the month's Adzuna or JSearch budget in minutes.
     */
    @PostMapping("/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger ingestion immediately (ADMIN only)",
            description = "Consumes external API quota; deliberately not available to ordinary users.")
    public IngestionReport ingest() {
        return ingestionService.ingestAll();
    }
}
