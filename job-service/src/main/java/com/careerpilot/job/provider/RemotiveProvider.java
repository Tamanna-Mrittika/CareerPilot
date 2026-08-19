package com.careerpilot.job.provider;

import com.careerpilot.job.domain.EmploymentType;
import com.careerpilot.job.domain.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remotive: remote-first tech roles, no API key.
 *
 * <p>Response is {@code {"jobs":[...]}} with sibling metadata keys. Salary arrives as free
 * text ("$120 - $170 /hour") which we keep verbatim rather than mis-parse into a number --
 * an hourly range silently stored as an annual figure would poison salary filtering.
 */
@Component
@Slf4j
public class RemotiveProvider implements JobProvider {

    private final WebClient webClient;
    private final ProviderProperties properties;

    public RemotiveProvider(WebClient.Builder builder, ProviderProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    public JobSource source() {
        return JobSource.REMOTIVE;
    }

    @Override
    public Flux<RawJob> fetch() {
        return webClient.get()
                .uri(properties.remotiveUrl())
                .header("User-Agent", properties.userAgent())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapIterable(root -> root.path("jobs"))
                .map(this::toRawJob)
                .doOnError(e -> log.warn("Remotive fetch failed: {}", e.toString()));
    }

    private RawJob toRawJob(JsonNode node) {
        Set<String> tags = new LinkedHashSet<>();
        node.path("tags").forEach(t -> tags.add(t.asText()));

        String category = node.path("category").asText(null);
        if (category != null && !category.isBlank()) {
            tags.add(category);
        }

        return RawJob.builder()
                .source(JobSource.REMOTIVE)
                .externalId(node.path("id").asText())
                .title(node.path("title").asText())
                .company(node.path("company_name").asText())
                .locationRaw(node.path("candidate_required_location").asText(null))
                // Every Remotive posting is remote by definition -- that is the whole board.
                .remote(true)
                .employmentType(EmploymentType.from(node.path("job_type").asText(null)))
                .description(node.path("description").asText(null))
                .salaryRaw(emptyToNull(node.path("salary").asText(null)))
                .applyUrl(node.path("url").asText())
                .tags(tags)
                .postedAt(parseDate(node.path("publication_date").asText(null)))
                .build();
    }

    /** Remotive sends local date-times with no offset; they are UTC in practice. */
    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            log.debug("Unparseable Remotive date '{}'", value);
            return null;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
