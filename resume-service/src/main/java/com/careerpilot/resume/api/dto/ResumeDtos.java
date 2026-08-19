package com.careerpilot.resume.api.dto;

import com.careerpilot.resume.domain.AtsCheckType;
import com.careerpilot.resume.domain.ResumeStatus;
import com.careerpilot.resume.domain.Severity;
import com.careerpilot.resume.domain.SuggestionCategory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record ResumeSummaryResponse(
            UUID id,
            String originalFilename,
            ResumeStatus status,
            Instant uploadedAt,
            Instant processedAt) {
    }

    public record ExtractedSkillResponse(
            String slug, String name, String category, int occurrenceCount) {
    }

    public record AtsCheckResponse(
            AtsCheckType type, Severity severity, String message) {
    }

    public record SuggestionResponse(
            SuggestionCategory category, Severity severity, String message, String evidence) {
    }

    public record ResumeDetailResponse(
            UUID id,
            String originalFilename,
            ResumeStatus status,
            String errorMessage,
            Integer inferredYearsExperience,
            List<ExtractedSkillResponse> extractedSkills,
            List<AtsCheckResponse> atsChecks,
            List<SuggestionResponse> suggestions,
            Instant uploadedAt,
            Instant processedAt) {
    }

    public record TermWeightResponse(String term, double weight) {
    }

    public record ScoreResponse(
            UUID resumeId,
            UUID jobId,
            String jobTitle,
            String jobCompany,
            double overallScore,
            List<TermWeightResponse> matchedTerms,
            List<TermWeightResponse> missingTerms,
            /**
             * Missing terms filtered to recognised skills only -- the list worth showing a
             * user as "what to add". Raw missingTerms ranks company names, cities and
             * hashtags highest (IDF rewards rarity), which is statistically correct but
             * useless as advice.
             */
            List<TermWeightResponse> actionableGaps) {
    }
}
