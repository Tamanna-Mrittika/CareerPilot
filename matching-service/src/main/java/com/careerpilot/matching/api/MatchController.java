package com.careerpilot.matching.api;

import com.careerpilot.common.error.ApiException;
import com.careerpilot.common.security.CurrentUser;
import com.careerpilot.matching.api.dto.MatchDtos.MatchPageResponse;
import com.careerpilot.matching.api.dto.MatchDtos.MatchResponse;
import com.careerpilot.matching.api.dto.MatchDtos.SkillGapResponse;
import com.careerpilot.matching.service.MatchingOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Ranked, explainable matches. Local and remote stay separate here for the same reason
 * they are separate in job-service: they back different product pages and merging them
 * lets remote volume bury the handful of local results.
 */
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
@Tag(name = "Matching")
public class MatchController {

    private static final int DEFAULT_LIMIT = 20;

    private final MatchingOrchestrator orchestrator;

    @Value("${careerpilot.search.default-city:Dhaka}")
    private String defaultCity;

    @GetMapping("/local")
    @Operation(summary = "Ranked local (on-site) job matches for the caller",
            description = "Defaults to the deployment's home city. Every match carries a per-component score breakdown.")
    public MatchPageResponse local(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return orchestrator.match(requireToken(), "LOCAL", q,
                city == null || city.isBlank() ? defaultCity : city, limit);
    }

    @GetMapping("/remote")
    @Operation(summary = "Ranked remote job matches for the caller",
            description = "No city filter: remote roles are open regardless of where the candidate sits.")
    public MatchPageResponse remote(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return orchestrator.match(requireToken(), "REMOTE", q, null, limit);
    }

    @GetMapping
    @Operation(summary = "Ranked matches across the whole corpus")
    public MatchPageResponse all(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_LIMIT) int limit) {
        return orchestrator.match(requireToken(), "ALL", q, city, limit);
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Fit score for one specific job",
            description = """
                    Returns the overall score plus the per-component breakdown (skills,
                    experience, location, work style), the matched and missing skills
                    ranked by how rare each is across current postings, and free courses
                    for each gap. A component score of -1 means it did not apply and was
                    excluded from the weighted average rather than counted as zero.
                    """)
    public MatchResponse forJob(@PathVariable UUID jobId) {
        return orchestrator.matchOne(requireToken(), jobId);
    }

    /**
     * The caller's own token, forwarded to profile-service to read their private profile.
     * Failing loudly here beats a confusing downstream 401: if the token cannot be
     * recovered, no user-scoped call can succeed anyway.
     */
    private String requireToken() {
        return CurrentUser.rawToken().orElseThrow(() -> new MissingTokenException());
    }

    private static class MissingTokenException extends ApiException {
        MissingTokenException() {
            super(HttpStatus.UNAUTHORIZED, "Unauthenticated",
                    "No bearer token present on this request.");
        }
    }
}
