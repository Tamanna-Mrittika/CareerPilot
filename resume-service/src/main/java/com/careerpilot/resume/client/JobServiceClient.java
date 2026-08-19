package com.careerpilot.resume.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads job-service's public listings. Nothing here is user-specific -- see
 * {@code job-service}'s {@code SecurityConfig} for why GET routes need no token; only
 * {@code POST /ingest} stays locked down there.
 */
@Component
@Slf4j
public class JobServiceClient {

    private final WebClient webClient;

    public JobServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("http://job-service").build();
    }

    /** Resume-service's own minimal view of a job -- only what ATS scoring needs. */
    public record JobSummary(UUID id, String title, String company, String description) {
    }

    private record JobPage(List<JobSummary> content, int page, int size, long totalElements,
                           int totalPages, boolean last) {
    }

    public Optional<JobSummary> fetchJob(UUID jobId) {
        try {
            JobSummary job = webClient.get()
                    .uri("/api/v1/jobs/{id}", jobId)
                    .retrieve()
                    .bodyToMono(JobSummary.class)
                    .timeout(Duration.ofSeconds(10))
                    .block(Duration.ofSeconds(15));
            return Optional.ofNullable(job);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch job {} from job-service: {}", jobId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * A sample of current job descriptions, used to build the IDF corpus for keyword
     * scoring (see {@code JobCorpusIdfCache}). Sampling rather than paging through the
     * whole corpus keeps the periodic refresh cheap; a few hundred postings is plenty to
     * estimate which terms are common versus rare across the market.
     */
    public List<String> sampleDescriptions(int sampleSize) {
        JobPage page = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/jobs")
                        .queryParam("size", sampleSize)
                        .build())
                .retrieve()
                .bodyToMono(JobPage.class)
                .timeout(Duration.ofSeconds(15))
                .onErrorResume(e -> {
                    log.warn("Failed to sample job descriptions from job-service: {}", e.toString());
                    return Mono.empty();
                })
                .block(Duration.ofSeconds(20));

        if (page == null || page.content() == null) {
            return List.of();
        }
        return page.content().stream()
                .map(JobSummary::description)
                .filter(d -> d != null && !d.isBlank())
                .toList();
    }
}
