package com.careerpilot.matching.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class MatchDtos {

    private MatchDtos() {
    }

    public record ComponentScoreResponse(
            String component,
            /** -1 means the component did not apply and was excluded from the weighted average. */
            double score,
            double weight,
            String explanation) {
    }

    public record MatchedSkillResponse(
            String slug, String name, String category, double rarityWeight, String proficiency) {
    }

    public record MissingSkillResponse(
            String slug, String name, String category, double rarityWeight, List<CourseResponse> courses) {
    }

    public record CourseResponse(String provider, String title, String url) {
    }

    public record JobRef(
            UUID id,
            String title,
            String company,
            String location,
            boolean remote,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            String applyUrl,
            /** Required by Remotive's and RemoteOK's API terms; the UI must display it. */
            String sourceAttribution) {
    }

    public record MatchResponse(
            JobRef job,
            double overallScore,
            List<ComponentScoreResponse> breakdown,
            List<MatchedSkillResponse> matchedSkills,
            List<MissingSkillResponse> missingSkills) {
    }

    /** Ranked list for the local or remote discovery page. */
    public record MatchPageResponse(
            int count,
            String scope,
            List<MatchResponse> matches) {
    }

    /**
     * Aggregate skill-gap view across many jobs -- answers "what should I learn next?"
     * rather than "how do I fit this one posting?".
     */
    public record SkillGapResponse(
            int jobsAnalysed,
            String scope,
            List<SkillGapEntry> gaps) {
    }

    public record SkillGapEntry(
            String slug,
            String name,
            String category,
            /** How many of the analysed postings asked for this skill. */
            int demandCount,
            /** Share of analysed postings, 0-100. */
            double demandPercentage,
            double rarityWeight,
            List<CourseResponse> courses) {
    }
}
