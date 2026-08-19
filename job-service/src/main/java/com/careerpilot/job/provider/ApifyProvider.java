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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Apify "job-board-scraper" actor -- the only source that actually returns Dhaka jobs.
 *
 * <p>Verified empirically before this class was written: a live run for
 * {@code location="Dhaka, Bangladesh"} returned real, current Bangladeshi postings via
 * LinkedIn. Two findings from that run shaped this adapter:
 *
 * <ul>
 *   <li>The actor's own <strong>BDJobs and Google boards return nothing</strong> and are
 *       excluded from its defaults, so despite BDJobs being the obvious Bangladesh source,
 *       LinkedIn is the board that works. Bdjobs content still arrives indirectly, since
 *       Bdjobs.com cross-posts to LinkedIn.</li>
 *   <li>LinkedIn results carry <strong>no description unless
 *       {@code linkedinFetchDescription} is set</strong>. Descriptions are not optional
 *       here -- they are the input to TF-IDF ATS scoring and skill extraction, so a job
 *       without one is nearly useless downstream. The flag is on by default.</li>
 * </ul>
 *
 * <p>Cost is the binding constraint, as with every provider in this service: the actor
 * bills roughly $0.003 per job against Apify's $5/month free credit, so about 1,600 jobs a
 * month. That is why ingestion runs on a schedule with a small {@code maxResults} rather
 * than querying per user search, and why this provider gets no retries.
 *
 * <p>Unlike the other adapters this is not a plain REST GET: an Apify actor is a job that
 * must be started and awaited. {@code run-sync-get-dataset-items} does both in one call,
 * but takes minutes rather than seconds, hence the dedicated longer timeout.
 */
@Component
@Slf4j
public class ApifyProvider implements JobProvider {

    private final WebClient webClient;
    private final ProviderProperties properties;

    public ApifyProvider(WebClient.Builder builder, ProviderProperties properties) {
        this.properties = properties;
        this.webClient = builder.build();
    }

    @Override
    public JobSource source() {
        return JobSource.APIFY;
    }

    @Override
    public boolean isEnabled() {
        return StringUtils.hasText(properties.apifyToken());
    }

    @Override
    public java.time.Duration fetchTimeout(ProviderProperties properties) {
        // The bug this class exists to avoid repeating: the ingestion service used to
        // apply one short timeout to every provider, which silently discarded an Apify
        // run that had already completed and been billed. This override is what makes
        // the long-running actor call actually work.
        return properties.apifyTimeout();
    }

    @Override
    public Flux<RawJob> fetch() {
        if (!isEnabled()) {
            log.info("Apify disabled: no API token. Local (Dhaka) listings will be unavailable.");
            return Flux.empty();
        }

        String url = "https://api.apify.com/v2/acts/%s/run-sync-get-dataset-items?token=%s"
                .formatted(properties.apifyActorId(), properties.apifyToken());

        return webClient.post()
                .uri(url)
                .bodyValue(buildInput())
                .retrieve()
                .bodyToMono(JsonNode.class)
                // No .timeout() here: JobIngestionService applies fetchTimeout() above to
                // the whole call. A second, different timeout here previously masked which
                // one was actually in effect.
                .flatMapIterable(root -> root)
                .map(this::toRawJob)
                .doOnError(e -> log.warn("Apify run failed: {}", e.toString()));
    }

    private Map<String, Object> buildInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("searchTerms", properties.apifySearchTerms());
        input.put("location", properties.apifyLocation());
        input.put("sites", properties.apifySites());
        input.put("maxResults", properties.apifyMaxResults());
        // Essential, not cosmetic: without descriptions there is nothing for the ATS
        // scorer or skill extractor to read.
        input.put("linkedinFetchDescription", true);
        input.put("descriptionFormat", "markdown");
        return input;
    }

    private RawJob toRawJob(JsonNode node) {
        Set<String> tags = new LinkedHashSet<>();
        String board = node.path("site").asText(null);
        if (StringUtils.hasText(board)) {
            tags.add(board);
        }
        String level = node.path("job_level").asText(null);
        if (StringUtils.hasText(level)) {
            tags.add(level);
        }

        String location = node.path("location").asText(null);

        return RawJob.builder()
                .source(JobSource.APIFY)
                .externalId(node.path("id").asText())
                .title(node.path("title").asText())
                .company(node.path("company").asText())
                .locationRaw(location)
                .locationCity(extractCity(location))
                .locationCountry(extractCountry(location))
                .remote(node.path("is_remote").asBoolean(false))
                .employmentType(EmploymentType.from(node.path("job_type").asText(null)))
                .description(node.path("description").asText(null))
                .salaryMin(positiveDecimal(node, "min_amount"))
                .salaryMax(positiveDecimal(node, "max_amount"))
                .salaryCurrency(node.path("currency").asText(null))
                .applyUrl(node.path("job_url").asText())
                .tags(tags)
                .postedAt(parseDatePosted(node.path("date_posted").asText(null)))
                .build();
    }

    /**
     * Location arrives as "Dhaka, Dhaka, Bangladesh" or "Dhaka, Bangladesh" -- the city is
     * the first segment in both.
     */
    private String extractCity(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        String first = location.split(",")[0].strip();
        return first.isEmpty() ? null : first;
    }

    /** Country is the last comma-separated segment. */
    private String extractCountry(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        String[] parts = location.split(",");
        if (parts.length < 2) {
            return null;
        }
        String last = parts[parts.length - 1].strip();
        return last.isEmpty() ? null : last;
    }

    private BigDecimal positiveDecimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && value.asDouble() > 0
                ? BigDecimal.valueOf(value.asDouble())
                : null;
    }

    /** {@code date_posted} is a bare date; treat it as midnight UTC. */
    private Instant parseDatePosted(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            log.debug("Unparseable Apify date '{}'", value);
            return null;
        }
    }
}
