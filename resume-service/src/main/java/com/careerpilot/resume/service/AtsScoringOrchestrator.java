package com.careerpilot.resume.service;

import com.careerpilot.common.error.BadRequestException;
import com.careerpilot.common.error.NotFoundException;
import com.careerpilot.resume.api.dto.ResumeDtos.ScoreResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.TermWeightResponse;
import com.careerpilot.resume.client.JobServiceClient;
import com.careerpilot.resume.client.JobServiceClient.JobSummary;
import com.careerpilot.resume.domain.ResumeStatus;
import com.careerpilot.resume.domain.ResumeUpload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * On-demand scoring: takes an already-processed resume's stored text and a live job's
 * description, and produces the IDF-weighted keyword match.
 *
 * <p>Deliberately not persisted per (resume, job) pair -- the job corpus changes
 * constantly and the resume's own text is already cached ({@code ResumeUpload.extractedText}),
 * so recomputing this on request is cheap and always reflects the current job posting
 * rather than a snapshot that could go stale.
 */
@Service
@RequiredArgsConstructor
public class AtsScoringOrchestrator {

    private final ResumeUploadService resumeUploadService;
    private final JobServiceClient jobServiceClient;
    private final AtsScoringService scoringService;

    @Transactional(readOnly = true)
    public ScoreResponse score(UUID userId, UUID resumeId, UUID jobId) {
        ResumeUpload resume = resumeUploadService.requireOwned(userId, resumeId);

        if (resume.getStatus() != ResumeStatus.COMPLETED) {
            throw new BadRequestException(
                    "Resume is not ready to be scored yet (status: " + resume.getStatus() + ").");
        }

        JobSummary job = jobServiceClient.fetchJob(jobId)
                .orElseThrow(() -> NotFoundException.of("Job", jobId));

        AtsScoringService.ScoreResult result = scoringService.score(resume.getExtractedText(), job.description());

        return new ScoreResponse(
                resumeId, jobId, job.title(), job.company(),
                result.overallScore(),
                result.matchedTerms().stream().map(t -> new TermWeightResponse(t.term(), round(t.weight()))).toList(),
                result.missingTerms().stream().map(t -> new TermWeightResponse(t.term(), round(t.weight()))).toList(),
                result.actionableGaps().stream().map(t -> new TermWeightResponse(t.term(), round(t.weight()))).toList());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
