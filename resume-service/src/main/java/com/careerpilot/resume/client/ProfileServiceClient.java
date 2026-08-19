package com.careerpilot.resume.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Reads profile-service's skill taxonomy. Nothing here is user-specific -- see
 * {@code profile-service}'s {@code SecurityConfig} for why this call needs no token.
 */
@Component
@Slf4j
public class ProfileServiceClient {

    private final WebClient webClient;

    public ProfileServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("http://profile-service").build();
    }

    /** One entry in the canonical skill taxonomy, as resume-service needs to see it. */
    public record SkillTaxonomyEntry(UUID id, String name, String slug, String category, List<String> aliases) {
    }

    public List<SkillTaxonomyEntry> fetchTaxonomy() {
        List<SkillTaxonomyEntry> result = webClient.get()
                .uri("/api/v1/skills/taxonomy")
                .retrieve()
                .bodyToFlux(SkillTaxonomyEntry.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch skill taxonomy from profile-service: {}", e.toString());
                    return Flux.empty();
                })
                .collectList()
                .block(Duration.ofSeconds(15));
        return result == null ? List.of() : result;
    }
}
