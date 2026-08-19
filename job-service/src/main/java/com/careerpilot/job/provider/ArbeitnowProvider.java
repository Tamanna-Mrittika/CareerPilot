package com.careerpilot.job.provider;

import com.careerpilot.job.domain.EmploymentType;
import com.careerpilot.job.domain.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Arbeitnow: EU-leaning board with visa-sponsorship signals, no API key.
 *
 * <p>Response is {@code {"data":[...], "links":..., "meta":...}}. Its {@code url} field can
 * point at the employer's own site rather than the posting, so the canonical link is built
 * from the slug instead -- otherwise "apply" would sometimes land on a company homepage.
 */
@Component
@Slf4j
public class ArbeitnowProvider implements JobProvider {

    private static final String JOB_URL_PREFIX = "https://www.arbeitnow.com/jobs/";

    private final WebClient webClient;
    private final ProviderProperties properties;

    public ArbeitnowProvider(WebClient.Builder builder, ProviderProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    public JobSource source() {
        return JobSource.ARBEITNOW;
    }

    @Override
    public Flux<RawJob> fetch() {
        return webClient.get()
                .uri(properties.arbeitnowUrl())
                .header("User-Agent", properties.userAgent())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapIterable(root -> root.path("data"))
                .map(this::toRawJob)
                .doOnError(e -> log.warn("Arbeitnow fetch failed: {}", e.toString()));
    }

    private RawJob toRawJob(JsonNode node) {
        Set<String> tags = new LinkedHashSet<>();
        node.path("tags").forEach(t -> tags.add(t.asText()));

        String employmentLabel = node.path("job_types").isArray() && !node.path("job_types").isEmpty()
                ? node.path("job_types").get(0).asText()
                : null;

        String slug = node.path("slug").asText();
        String location = node.path("location").asText(null);

        return RawJob.builder()
                .source(JobSource.ARBEITNOW)
                .externalId(slug)
                .title(node.path("title").asText())
                .company(node.path("company_name").asText())
                .locationRaw(location)
                .locationCity(extractCity(location))
                .remote(node.path("remote").asBoolean(false))
                .employmentType(EmploymentType.from(employmentLabel))
                .description(node.path("description").asText(null))
                .applyUrl(JOB_URL_PREFIX + slug)
                .tags(tags)
                .postedAt(parseEpochSeconds(node.path("created_at").asLong(0)))
                .build();
    }

    /**
     * Arbeitnow's location is sometimes a bare city ("Berlin") and sometimes a full street
     * address ("Berlin - Berliner Strasse 80, 13189 Berlin"). The leading segment is the
     * city in both, so taking it makes city filtering work without a geocoder.
     */
    private String extractCity(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String first = location.split("[-,]")[0].strip();
        return first.isEmpty() ? null : first;
    }

    private Instant parseEpochSeconds(long epochSeconds) {
        return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : null;
    }
}
