package com.careerpilot.job.provider;

import com.careerpilot.job.domain.EmploymentType;
import com.careerpilot.job.domain.JobSource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JSearch: the only configured source that can return <strong>Bangladesh</strong> postings.
 *
 * <p>Why this provider exists at all: none of the other four cover Bangladesh. Adzuna's 16
 * countries exclude it, Remotive and RemoteOK are remote-only, and Arbeitnow is EU-focused.
 * JSearch runs on top of Google for Jobs, which does index Bdjobs, LinkedIn and the local
 * boards, so a free-text query like "software engineer in Dhaka, Bangladesh" is the
 * realistic route to local listings.
 *
 * <p><strong>Caveat worth knowing:</strong> {@code bd} does not appear in JSearch's
 * documented country-code list, so coverage is reached through the free-text query rather
 * than the country parameter, and the actual yield for Dhaka is unverified until a key is
 * configured. If it returns nothing useful, this class is the only one that needs replacing
 * -- the {@link JobProvider} abstraction keeps that blast radius to a single file.
 *
 * <p>Quota: the free tier is 200 requests per MONTH, tighter even than Adzuna's. Each
 * configured query costs one request per run, so the query list is deliberately short.
 */
@Component
@Slf4j
public class JSearchProvider implements JobProvider {

    private final WebClient webClient;
    private final ProviderProperties properties;

    public JSearchProvider(WebClient.Builder builder, ProviderProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    public JobSource source() {
        return JobSource.JSEARCH;
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(properties.jsearchApiKey());
    }

    @Override
    public Flux<RawJob> fetch() {
        if (!isEnabled()) {
            log.info("JSearch disabled: no API key set. Local (Bangladesh) listings will be "
                    + "unavailable; only remote boards will ingest.");
            return Flux.empty();
        }

        List<String> queries = properties.jsearchQueries();
        return Flux.fromIterable(queries)
                // Sequential: 200 requests a month is the tightest budget of any provider.
                .concatMap(this::fetchQuery)
                .doOnError(e -> log.warn("JSearch fetch failed: {}", e.toString()));
    }

    private Flux<RawJob> fetchQuery(String query) {
        return webClient.get()
                .uri(properties.jsearchBaseUrl(), uriBuilder -> uriBuilder
                        .queryParam("query", query)
                        .queryParam("page", 1)
                        .queryParam("num_pages", 1)
                        .queryParam("date_posted", "month")
                        .build())
                .header("x-api-key", properties.jsearchApiKey())
                .header("User-Agent", properties.userAgent())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapIterable(root -> root.path("data"))
                .map(this::toRawJob)
                .doOnComplete(() -> log.debug("JSearch query completed: {}", query));
    }

    private RawJob toRawJob(JsonNode node) {
        Set<String> tags = new LinkedHashSet<>();
        node.path("required_technologies").forEach(t -> tags.add(t.asText()));
        node.path("soft_skills").forEach(t -> tags.add(t.asText()));

        String city = node.path("job_city").asText(null);
        String country = node.path("job_country").asText(null);
        String locationDisplay = node.path("job_location").asText(null);
        if (!StringUtils.hasText(locationDisplay)) {
            locationDisplay = joinLocation(city, country);
        }

        return RawJob.builder()
                .source(JobSource.JSEARCH)
                .externalId(node.path("job_id").asText())
                .title(node.path("job_title").asText())
                .company(node.path("employer_name").asText())
                .locationRaw(locationDisplay)
                .locationCity(city)
                .locationCountry(country)
                .remote(node.path("job_is_remote").asBoolean(false))
                .employmentType(EmploymentType.from(node.path("job_employment_type").asText(null)))
                .description(node.path("job_description").asText(null))
                .salaryMin(positiveDecimal(node, "job_min_salary"))
                .salaryMax(positiveDecimal(node, "job_max_salary"))
                .salaryCurrency(node.path("job_salary_currency").asText(null))
                .applyUrl(node.path("job_apply_link").asText())
                .tags(tags)
                .postedAt(parsePostedAt(node))
                .build();
    }

    private String joinLocation(String city, String country) {
        if (StringUtils.hasText(city) && StringUtils.hasText(country)) {
            return city + ", " + country;
        }
        return StringUtils.hasText(city) ? city : country;
    }

    private BigDecimal positiveDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && value.asDouble() > 0
                ? BigDecimal.valueOf(value.asDouble())
                : null;
    }

    /** JSearch supplies both an ISO timestamp and an epoch; prefer whichever is present. */
    private Instant parsePostedAt(JsonNode node) {
        String iso = node.path("job_posted_at_datetime_utc").asText(null);
        if (StringUtils.hasText(iso)) {
            try {
                return OffsetDateTime.parse(iso).toInstant();
            } catch (DateTimeParseException e) {
                log.debug("Unparseable JSearch date '{}'", iso);
            }
        }
        long epoch = node.path("job_posted_at_timestamp").asLong(0);
        return epoch > 0 ? Instant.ofEpochSecond(epoch) : null;
    }
}
