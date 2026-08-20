package com.careerpilot.matching.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads job-service's public listings -- no token needed (see job-service's SecurityConfig). */
@Component
@Slf4j
public class JobServiceClient {

    private final WebClient webClient;

    public JobServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("http://job-service").build();
    }

    /** matching-service's own view of a job -- only what fit scoring reads. */
    public record JobSummary(
            UUID id,
            String title,
            String company,
            String location,
            String city,
            String country,
            boolean remote,
            String employmentType,
            String description,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String applyUrl,
            List<String> tags,
            String source,
            String sourceAttribution) {
    }

    private record JobPage(List<JobSummary> content, int page, int size, long totalElements,
                           int totalPages, boolean last) {
    }

    public Optional<JobSummary> fetchJob(UUID jobId) {
        try {
            return Optional.ofNullable(webClient.get()
                    .uri("/api/v1/jobs/{id}", jobId)
                    .retrieve()
                    .bodyToMono(JobSummary.class)
                    .timeout(Duration.ofSeconds(10))
                    .block(Duration.ofSeconds(15)));
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch job {}: {}", jobId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Fetches a page of candidate jobs to rank.
     *
     * <p>{@code scope} maps to job-service's own LOCAL/REMOTE/ALL split, so the two
     * separate product pages (Dhaka jobs vs remote jobs) each get their own ranked list
     * rather than one merged list where remote volume buries local results.
     */
    public List<JobSummary> fetchCandidates(String scope, String query, String city, int limit) {
        JobPage page = webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/jobs").queryParam("size", limit);
                    if (scope != null && !scope.isBlank()) {
                        uriBuilder.queryParam("scope", scope);
                    }
                    if (query != null && !query.isBlank()) {
                        uriBuilder.queryParam("q", query);
                    }
                    if (city != null && !city.isBlank()) {
                        uriBuilder.queryParam("city", city);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .bodyToMono(JobPage.class)
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch candidate jobs: {}", e.toString());
                    return Mono.empty();
                })
                .block(Duration.ofSeconds(20));

        return page == null || page.content() == null ? List.of() : page.content();
    }
}
