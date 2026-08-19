package com.careerpilot.resume.service;

import com.careerpilot.resume.api.dto.ResumeDtos.AtsCheckResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.ExtractedSkillResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.ResumeDetailResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.ResumeSummaryResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.SuggestionResponse;
import com.careerpilot.resume.domain.ResumeUpload;

/** Entity to DTO translation, kept out of the controller and services. */
final class ResumeMapper {

    private ResumeMapper() {
    }

    static ResumeSummaryResponse toSummary(ResumeUpload upload) {
        return new ResumeSummaryResponse(
                upload.getId(), upload.getOriginalFilename(), upload.getStatus(),
                upload.getUploadedAt(), upload.getProcessedAt());
    }

    static ResumeDetailResponse toDetail(ResumeUpload upload) {
        return new ResumeDetailResponse(
                upload.getId(),
                upload.getOriginalFilename(),
                upload.getStatus(),
                upload.getErrorMessage(),
                upload.getInferredYearsExperience(),
                upload.getExtractedSkills().stream()
                        .map(s -> new ExtractedSkillResponse(
                                s.getSkillSlug(), s.getSkillName(), s.getCategory(), s.getOccurrenceCount()))
                        .toList(),
                upload.getAtsChecks().stream()
                        .map(c -> new AtsCheckResponse(c.getCheckType(), c.getSeverity(), c.getMessage()))
                        .toList(),
                upload.getSuggestions().stream()
                        .map(s -> new SuggestionResponse(
                                s.getCategory(), s.getSeverity(), s.getMessage(), s.getEvidence()))
                        .toList(),
                upload.getUploadedAt(),
                upload.getProcessedAt());
    }
}
