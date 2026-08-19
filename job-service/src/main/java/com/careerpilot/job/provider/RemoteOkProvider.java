package com.careerpilot.job.provider;

import com.careerpilot.job.domain.EmploymentType;
import com.careerpilot.job.domain.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RemoteOK: remote roles, no API key.
 *
 * <p>Two traps in this feed, both verified against the live response:
 * <ul>
 *   <li>The top level is a bare array whose <strong>first element is a legal notice</strong>,
 *       not a job. Parsing it as a posting yields a row with no title or company.</li>
 *   <li>Fields are named {@code position} and {@code company}, not {@code title} and
 *       {@code company_name} as the other boards use.</li>
 * </ul>
 *
 * <p>Salary arrives as numeric min/max where {@code 0} means "not disclosed" rather than
 * "unpaid", so zeros are dropped instead of stored.
 */
@Component
@Slf4j
public class RemoteOkProvider implements JobProvider {

    private final WebClient webClient;
    private final ProviderProperties properties;

    public RemoteOkProvider(WebClient.Builder builder, ProviderProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    public JobSource source() {
        return JobSource.REMOTEOK;
    }

    @Override
    public Flux<RawJob> fetch() {
        return webClient.get()
                .uri(properties.remoteOkUrl())
                // RemoteOK rejects requests that arrive without a User-Agent.
                .header("User-Agent", properties.userAgent())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapIterable(this::stripLegalNotice)
                .map(this::toRawJob)
                .doOnError(e -> log.warn("RemoteOK fetch failed: {}", e.toString()));
    }

    /**
     * Drops the leading legal-notice element. Detected by required fields being absent
     * rather than by index, so a change in element order cannot silently reintroduce a
     * phantom job with no title.
     */
    private List<JsonNode> stripLegalNotice(JsonNode root) {
        List<JsonNode> jobs = new ArrayList<>();
        for (JsonNode node : root) {
            if (node.hasNonNull("id") && node.hasNonNull("position")) {
                jobs.add(node);
            }
        }
        return jobs;
    }

    private RawJob toRawJob(JsonNode node) {
        Set<String> tags = new LinkedHashSet<>();
        node.path("tags").forEach(t -> tags.add(t.asText()));

        long salaryMin = node.path("salary_min").asLong(0);

        return RawJob.builder()
                .source(JobSource.REMOTEOK)
                .externalId(node.path("id").asText())
                .title(node.path("position").asText())
                .company(node.path("company").asText())
                .locationRaw(emptyToNull(node.path("location").asText(null)))
                .remote(true)
                .employmentType(EmploymentType.from(employmentTagOrNull(tags)))
                .description(node.path("description").asText(null))
                .salaryMin(positiveOrNull(salaryMin))
                .salaryMax(positiveOrNull(node.path("salary_max").asLong(0)))
                .salaryCurrency(salaryMin > 0 ? "USD" : null)
                .applyUrl(node.path("url").asText(node.path("apply_url").asText()))
                .tags(tags)
                .postedAt(parseEpoch(node.path("epoch").asLong(0)))
                .build();
    }

    private static String employmentTagOrNull(Set<String> tags) {
        return tags.stream()
                .filter(t -> {
                    String lower = t.toLowerCase();
                    return lower.contains("time") || lower.contains("contract");
                })
                .findFirst()
                .orElse(null);
    }

    /** Zero means "not disclosed" in this feed, not a genuine zero salary. */
    private static BigDecimal positiveOrNull(long value) {
        return value > 0 ? BigDecimal.valueOf(value) : null;
    }

    private static Instant parseEpoch(long epochSeconds) {
        return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
