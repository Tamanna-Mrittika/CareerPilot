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
import java.util.Locale;
import java.util.Set;

/**
 * Adzuna: the only source with real salary figures and genuine (non-remote-only) locations,
 * covering 16 countries. Requires a free API key.
 *
 * <p>Quota is the design constraint here. The free tier allows roughly 1,000 calls per
 * MONTH -- about 33 a day. That is precisely why this provider pulls a small fixed number
 * of pages on a schedule while everything downstream is cached, rather than querying per
 * user search: one busy afternoon of live queries would exhaust the month's budget.
 *
 * <p>With no credentials {@link #isEnabled()} returns false, so a fresh clone still ingests
 * from the three keyless boards instead of failing.
 */
@Component
@Slf4j
public class AdzunaProvider implements JobProvider {

    private final WebClient webClient;
    private final ProviderProperties properties;

    public AdzunaProvider(WebClient.Builder builder, ProviderProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    public JobSource source() {
        return JobSource.ADZUNA;
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(properties.adzunaAppId())
                && StringUtils.hasText(properties.adzunaAppKey());
    }

    @Override
    public Flux<RawJob> fetch() {
        if (!isEnabled()) {
            log.debug("Adzuna disabled: no API credentials configured");
            return Flux.empty();
        }

        return Flux.range(1, properties.adzunaPages())
                // Sequential rather than parallel: concurrent pages burn the monthly quota
                // faster and Adzuna rate-limits bursts.
                .concatMap(this::fetchPage)
                .doOnError(e -> log.warn("Adzuna fetch failed: {}", e.toString()));
    }

    private Flux<RawJob> fetchPage(int page) {
        String url = "%s/%s/search/%d".formatted(
                properties.adzunaBaseUrl(), properties.adzunaCountry(), page);

        return webClient.get()
                .uri(url, uriBuilder -> uriBuilder
                        .queryParam("app_id", properties.adzunaAppId())
                        .queryParam("app_key", properties.adzunaAppKey())
                        .queryParam("results_per_page", 50)
                        .queryParam("content-type", "application/json")
                        .build())
                .header("User-Agent", properties.userAgent())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapIterable(root -> root.path("results"))
                .map(this::toRawJob);
    }

    private RawJob toRawJob(JsonNode node) {
        Set<String> tags = new LinkedHashSet<>();
        String category = node.path("category").path("label").asText(null);
        if (StringUtils.hasText(category)) {
            tags.add(category);
        }

        String locationDisplay = node.path("location").path("display_name").asText(null);
        // Adzuna's area array runs broad to narrow: ["UK", "London", "Camden"].
        JsonNode area = node.path("location").path("area");
        String country = area.isArray() && !area.isEmpty() ? area.get(0).asText(null) : null;
        // area runs broad to narrow, so the last element is the most specific place name.
        String city = area.isArray() && area.size() > 1
                ? area.get(area.size() - 1).asText(null) : null;

        String contract = node.path("contract_time").asText(null);
        if (!StringUtils.hasText(contract)) {
            contract = node.path("contract_type").asText(null);
        }

        return RawJob.builder()
                .source(JobSource.ADZUNA)
                .externalId(node.path("id").asText())
                .title(node.path("title").asText())
                .company(node.path("company").path("display_name").asText())
                .locationRaw(locationDisplay)
                .locationCity(city)
                .locationCountry(country)
                .remote(looksRemote(node.path("title").asText("") + " " + locationDisplay))
                .employmentType(EmploymentType.from(contract))
                .description(node.path("description").asText(null))
                .salaryMin(positiveDecimal(node, "salary_min"))
                .salaryMax(positiveDecimal(node, "salary_max"))
                .salaryCurrency(currencyFor(properties.adzunaCountry()))
                .applyUrl(node.path("redirect_url").asText())
                .tags(tags)
                .postedAt(parseCreated(node.path("created").asText(null)))
                .build();
    }

    /** Adzuna exposes no remote flag, so it is inferred from title and location text. */
    private boolean looksRemote(String text) {
        String lower = text.toLowerCase(Locale.ENGLISH);
        return lower.contains("remote") || lower.contains("work from home");
    }

    private BigDecimal positiveDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && value.asDouble() > 0
                ? BigDecimal.valueOf(value.asDouble())
                : null;
    }

    private String currencyFor(String country) {
        return switch (country.toLowerCase(Locale.ENGLISH)) {
            case "gb" -> "GBP";
            case "us" -> "USD";
            case "au" -> "AUD";
            case "ca" -> "CAD";
            case "in" -> "INR";
            case "za" -> "ZAR";
            case "de", "fr", "nl", "at", "es", "it", "pl" -> "EUR";
            default -> null;
        };
    }

    private Instant parseCreated(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            log.debug("Unparseable Adzuna date '{}'", value);
            return null;
        }
    }
}
