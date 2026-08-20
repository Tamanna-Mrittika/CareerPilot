package com.careerpilot.matching.api;

import com.careerpilot.common.error.ApiException;
import com.careerpilot.common.security.CurrentUser;
import com.careerpilot.matching.api.dto.MatchDtos.SkillGapResponse;
import com.careerpilot.matching.service.MatchingOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "What should I learn next?" -- the aggregate view across many postings, as opposed to
 * the per-job gaps on {@code /api/v1/matches}.
 */
@RestController
@RequestMapping("/api/v1/skill-gap")
@RequiredArgsConstructor
@Tag(name = "Skill gap")
public class SkillGapController {

    private final MatchingOrchestrator orchestrator;

    @Value("${careerpilot.search.default-city:Dhaka}")
    private String defaultCity;

    @GetMapping
    @Operation(summary = "Skills the market asks for that the caller does not have",
            description = """
                    Ranked by how many analysed postings ask for each skill -- deliberately
                    the opposite of how a single job's gaps are ranked. For deciding what to
                    study, a skill 40% of employers want beats an exotic one that appears
                    once, even though the exotic one scores higher on any individual match.
                    Each gap carries free course recommendations where the catalog has them.
                    """)
    public SkillGapResponse skillGap(
            @RequestParam(required = false, defaultValue = "ALL") String scope,
            @RequestParam(required = false) String city) {
        String resolvedCity = "LOCAL".equalsIgnoreCase(scope) && (city == null || city.isBlank())
                ? defaultCity : city;
        return orchestrator.skillGap(requireToken(), scope.toUpperCase(java.util.Locale.ENGLISH), resolvedCity);
    }

    private String requireToken() {
        return CurrentUser.rawToken().orElseThrow(MissingTokenException::new);
    }

    private static class MissingTokenException extends ApiException {
        MissingTokenException() {
            super(HttpStatus.UNAUTHORIZED, "Unauthenticated", "No bearer token present on this request.");
        }
    }
}
