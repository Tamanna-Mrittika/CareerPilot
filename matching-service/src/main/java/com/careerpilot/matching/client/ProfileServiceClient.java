package com.careerpilot.matching.client;

import com.careerpilot.common.error.UpstreamUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Reads the caller's own profile and the shared skill taxonomy.
 *
 * <p>The profile call forwards the caller's own JWT, because
 * {@code GET /api/v1/profiles/me} is user-scoped private data -- profile-service resolves
 * "me" from the token subject, so without it there is no way to say whose profile is
 * wanted. The taxonomy call needs no token (profile-service makes GET /skills public).
 */
@Component
@Slf4j
public class ProfileServiceClient {

    private final WebClient webClient;

    public ProfileServiceClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.webClient = loadBalancedWebClientBuilder.baseUrl("http://profile-service").build();
    }

    /** Only the fields fit scoring actually reads -- matching-service owns its own view. */
    public record ProfileSnapshot(
            UUID userId,
            String fullName,
            String headline,
            String locationCity,
            String locationCountry,
            String remotePreference,
            Integer yearsExperience,
            List<ProfileSkill> skills) {
    }

    public record ProfileSkill(
            String slug,
            String name,
            String category,
            String proficiency,
            Integer yearsExperience) {
    }

    public record SkillTaxonomyEntry(UUID id, String name, String slug, String category, List<String> aliases) {
    }

    /**
     * Fetches the caller's profile. Unlike the taxonomy and job reads, a failure here is
     * fatal to the request -- there is no meaningful "partial" fit score without the
     * candidate's own skills, so this throws rather than degrading to an empty result that
     * would silently look like "you match nothing".
     */
    public ProfileSnapshot fetchMyProfile(String bearerToken) {
        try {
            ProfileSnapshot profile = webClient.get()
                    .uri("/api/v1/profiles/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .bodyToMono(ProfileSnapshot.class)
                    .timeout(Duration.ofSeconds(10))
                    .block(Duration.ofSeconds(15));

            if (profile == null) {
                throw new UpstreamUnavailableException("profile-service returned no profile data");
            }
            return profile;
        } catch (UpstreamUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch profile from profile-service: {}", e.toString());
            throw new UpstreamUnavailableException(
                    "Could not load your profile right now. Please try again shortly.");
        }
    }

    /** Full taxonomy with aliases, for deriving each job's implied skill set from its description. */
    public List<SkillTaxonomyEntry> fetchTaxonomy() {
        List<SkillTaxonomyEntry> result = webClient.get()
                .uri("/api/v1/skills/taxonomy")
                .retrieve()
                .bodyToFlux(SkillTaxonomyEntry.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch skill taxonomy: {}", e.toString());
                    return Flux.empty();
                })
                .collectList()
                .block(Duration.ofSeconds(15));
        return result == null ? List.of() : result;
    }
}
