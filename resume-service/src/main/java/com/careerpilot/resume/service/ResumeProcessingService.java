package com.careerpilot.resume.service;

import com.careerpilot.resume.domain.AtsCheck;
import com.careerpilot.resume.domain.ResumeUpload;
import com.careerpilot.resume.domain.Severity;
import com.careerpilot.resume.domain.Suggestion;
import com.careerpilot.resume.domain.SuggestionCategory;
import com.careerpilot.resume.repository.ResumeUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs the actual parsing pipeline off the request thread.
 *
 * <p>This lives in its own bean, separate from {@link ResumeUploadService}, for the same
 * reason {@code JobPersistenceService} is separate from {@code JobIngestionService} in
 * job-service: both {@code @Async} and {@code @Transactional} are Spring AOP proxies, and a
 * method called from another method on the <em>same</em> bean bypasses the proxy entirely
 * -- silently running synchronously instead of asynchronously. Calling
 * {@link #processAsync(UUID)} through this separate bean is what makes the 202-then-poll
 * flow real rather than accidental dead code.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeProcessingService {

    private final ResumeUploadRepository resumeUploads;
    private final MinioStorageService storageService;
    private final TikaTextExtractor textExtractor;
    private final PdfStructuralAnalyzer structuralAnalyzer;
    private final SkillExtractionService skillExtractionService;
    private final ResumeSectionAnalyzer sectionAnalyzer;
    private final ResumeFeedbackService feedbackService;

    @Async("resumeProcessingExecutor")
    @Transactional
    public void processAsync(UUID resumeUploadId) {
        ResumeUpload upload = resumeUploads.findById(resumeUploadId).orElse(null);
        if (upload == null) {
            log.warn("Resume upload {} vanished before processing could start", resumeUploadId);
            return;
        }

        upload.markProcessing();
        resumeUploads.save(upload);

        try {
            byte[] pdfBytes = storageService.retrieve(upload.getStorageObjectKey());
            String text = textExtractor.extractText(pdfBytes);

            upload.setExtractedText(text);
            upload.setInferredYearsExperience(sectionAnalyzer.inferYearsExperience(text));

            persistSkills(upload, text);
            persistStructuralChecks(upload, pdfBytes, text);
            persistSuggestions(upload, text);

            upload.markCompleted();
            resumeUploads.save(upload);
            log.info("Resume {} processed successfully ({} chars extracted)",
                    resumeUploadId, text.length());
        } catch (Exception e) {
            // A parsing failure is a normal, expected outcome for a bad or unusual PDF --
            // it must land the upload in FAILED with a clear reason, not propagate and
            // leave the row stuck in PROCESSING forever.
            log.warn("Resume {} processing failed: {}", resumeUploadId, e.toString());
            upload.markFailed(e.getMessage());
            resumeUploads.save(upload);
        }
    }

    private void persistSkills(ResumeUpload upload, String text) {
        for (SkillExtractionService.SkillMatch match : skillExtractionService.extract(text)) {
            upload.getExtractedSkills().add(com.careerpilot.resume.domain.ExtractedSkill.create(
                    upload, match.skill().slug(), match.skill().name(),
                    match.skill().category(), match.occurrenceCount()));
        }
    }

    private void persistStructuralChecks(ResumeUpload upload, byte[] pdfBytes, String text) {
        for (PdfStructuralAnalyzer.Finding finding : structuralAnalyzer.analyze(pdfBytes, text)) {
            upload.getAtsChecks().add(AtsCheck.create(
                    upload, finding.type(), finding.severity(), finding.message()));
        }
    }

    private void persistSuggestions(ResumeUpload upload, String text) {
        for (ResumeSectionAnalyzer.MissingSection missing : sectionAnalyzer.findMissingSections(text)) {
            upload.getSuggestions().add(Suggestion.create(
                    upload, SuggestionCategory.MISSING_SECTION, Severity.WARNING,
                    missing.message(), null));
        }
        for (ResumeFeedbackService.Finding finding : feedbackService.analyze(text)) {
            upload.getSuggestions().add(Suggestion.create(
                    upload, finding.category(), finding.severity(), finding.message(), finding.evidence()));
        }
    }
}
