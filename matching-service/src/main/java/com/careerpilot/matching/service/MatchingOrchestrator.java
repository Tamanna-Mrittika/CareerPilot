package com.careerpilot.matching.service;

import com.careerpilot.common.error.NotFoundException;
import com.careerpilot.matching.api.dto.MatchDtos.ComponentScoreResponse;
import com.careerpilot.matching.api.dto.MatchDtos.CourseResponse;
import com.careerpilot.matching.api.dto.MatchDtos.JobRef;
import com.careerpilot.matching.api.dto.MatchDtos.MatchPageResponse;
import com.careerpilot.matching.api.dto.MatchDtos.MatchResponse;
import com.careerpilot.matching.api.dto.MatchDtos.MatchedSkillResponse;
import com.careerpilot.matching.api.dto.MatchDtos.MissingSkillResponse;
import com.careerpilot.matching.api.dto.MatchDtos.SkillGapEntry;
import com.careerpilot.matching.api.dto.MatchDtos.SkillGapResponse;
import com.careerpilot.matching.client.JobServiceClient;
import com.careerpilot.matching.client.JobServiceClient.JobSummary;
import com.careerpilot.matching.client.ProfileServiceClient;
import com.careerpilot.matching.client.ProfileServiceClient.ProfileSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Composes profile-service and job-service into ranked, explainable matches.
 *
 * <p>This is the API-composition service in the architecture: it owns almost no data of
 * its own (only the course catalog) and exists to combine two peers' data into something
 * neither can produce alone. Scores are computed fresh per request rather than persisted,
 * for the same reason resume-service does not cache ATS scores -- both sides change
 * independently and a stored score would need invalidating on every profile edit and
 * every ingestion run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingOrchestrator {

    /** How many postings to rank. Bounded: this is a demo-scale corpus, not a search engine. */
    private static final int CANDIDATE_LIMIT = 60;

    private final ProfileServiceClient profileServiceClient;
    private final JobServiceClient jobServiceClient;
    private final FitScoreService fitScoreService;
    private final JobSkillExtractor skillExtractor;
    private final SkillRarityIndex rarityIndex;
    private final CourseRecommendationService courseService;

    /** Ranked matches for one scope (LOCAL / REMOTE / ALL). */
    public MatchPageResponse match(String bearerToken, String scope, String query, String city, int limit) {
        ProfileSnapshot profile = profileServiceClient.fetchMyProfile(bearerToken);
        List<JobSummary> candidates = jobServiceClient.fetchCandidates(scope, query, city, CANDIDATE_LIMIT);

        if (candidates.isEmpty()) {
            return new MatchPageResponse(0, scope, List.of());
        }

        // One batched course lookup for every gap across every job, rather than per job.
        List<FitScoreService.FitResult> results = candidates.stream()
                .map(job -> fitScoreService.score(profile, job))
                .toList();

        Set<String> allMissingSlugs = new LinkedHashSet<>();
        results.forEach(r -> r.missingSkills().forEach(m -> allMissingSlugs.add(m.slug())));
        Map<String, List<CourseResponse>> coursesBySlug = courseService.findForSkills(allMissingSlugs);

        List<MatchResponse> matches = new java.util.ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            matches.add(toResponse(candidates.get(i), results.get(i), coursesBySlug));
        }

        matches.sort(Comparator.comparingDouble(MatchResponse::overallScore).reversed());
        List<MatchResponse> top = matches.size() > limit ? matches.subList(0, limit) : matches;

        return new MatchPageResponse(top.size(), scope, List.copyOf(top));
    }

    /** Fit for one specific job. */
    public MatchResponse matchOne(String bearerToken, UUID jobId) {
        ProfileSnapshot profile = profileServiceClient.fetchMyProfile(bearerToken);
        JobSummary job = jobServiceClient.fetchJob(jobId)
                .orElseThrow(() -> NotFoundException.of("Job", jobId));

        FitScoreService.FitResult result = fitScoreService.score(profile, job);
        Map<String, List<CourseResponse>> courses = courseService.findForSkills(
                result.missingSkills().stream().map(FitScoreService.MissingSkill::slug).toList());

        return toResponse(job, result, courses);
    }

    /**
     * Aggregate skill gap across the whole candidate set: which skills the market asks for
     * most often that this candidate does not have.
     *
     * <p>Ranked by <em>demand frequency</em>, not rarity -- deliberately the opposite of
     * how a single job's gaps are ranked. For "what should I learn next?", a skill 40% of
     * postings want is a better use of study time than an exotic one that appears once,
     * even though the exotic one scores higher on any individual match.
     */
    public SkillGapResponse skillGap(String bearerToken, String scope, String city) {
        ProfileSnapshot profile = profileServiceClient.fetchMyProfile(bearerToken);
        List<JobSummary> candidates = jobServiceClient.fetchCandidates(scope, null, city, CANDIDATE_LIMIT);

        if (candidates.isEmpty()) {
            return new SkillGapResponse(0, scope, List.of());
        }

        Set<String> held = new HashSet<>();
        if (profile.skills() != null) {
            profile.skills().forEach(s -> held.add(s.slug()));
        }

        Map<String, Integer> demand = new HashMap<>();
        for (JobSummary job : candidates) {
            for (String slug : skillExtractor.extractSkillSlugs(job)) {
                if (!held.contains(slug)) {
                    demand.merge(slug, 1, Integer::sum);
                }
            }
        }

        Map<String, List<CourseResponse>> courses = courseService.findForSkills(demand.keySet());

        List<SkillGapEntry> gaps = demand.entrySet().stream()
                .map(entry -> {
                    var taxonomyEntry = skillExtractor.resolveSlug(entry.getKey());
                    return new SkillGapEntry(
                            entry.getKey(),
                            taxonomyEntry != null ? taxonomyEntry.name() : entry.getKey(),
                            taxonomyEntry != null ? taxonomyEntry.category() : "Unknown",
                            entry.getValue(),
                            round((entry.getValue() * 100.0) / candidates.size()),
                            round(rarityIndex.weight(entry.getKey())),
                            courses.getOrDefault(entry.getKey(), List.of()));
                })
                .sorted(Comparator.comparingInt(SkillGapEntry::demandCount).reversed())
                .toList();

        return new SkillGapResponse(candidates.size(), scope, gaps);
    }

    private MatchResponse toResponse(JobSummary job, FitScoreService.FitResult result,
                                     Map<String, List<CourseResponse>> coursesBySlug) {
        return new MatchResponse(
                new JobRef(job.id(), job.title(), job.company(), job.location(), job.remote(),
                        job.salaryMin(), job.salaryMax(), job.salaryCurrency(), job.applyUrl(),
                        job.sourceAttribution()),
                result.overallScore(),
                result.breakdown().stream()
                        .map(c -> new ComponentScoreResponse(c.component(), c.score(), c.weight(), c.explanation()))
                        .toList(),
                result.matchedSkills().stream()
                        .map(m -> new MatchedSkillResponse(m.slug(), m.name(), m.category(),
                                m.rarityWeight(), m.proficiency()))
                        .toList(),
                result.missingSkills().stream()
                        .map(m -> new MissingSkillResponse(m.slug(), m.name(), m.category(), m.rarityWeight(),
                                coursesBySlug.getOrDefault(m.slug(), List.of())))
                        .toList());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
