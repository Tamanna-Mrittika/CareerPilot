package com.careerpilot.resume.service;

import com.careerpilot.common.error.BadRequestException;
import com.careerpilot.common.error.NotFoundException;
import com.careerpilot.resume.api.dto.ResumeDtos.ResumeDetailResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.ResumeSummaryResponse;
import com.careerpilot.resume.domain.ResumeUpload;
import com.careerpilot.resume.repository.ResumeUploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * The upload half of the async flow: validate, store, create the tracking row, hand off to
 * {@link ResumeProcessingService} for the actual parsing, and return immediately. The
 * controller turns the result into a 202 with a {@code Location} header pointing at the
 * status endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeUploadService {

    /** First four bytes of any valid PDF -- checked against the actual bytes, never the client-declared content type. */
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final ResumeUploadRepository resumeUploads;
    private final MinioStorageService storageService;
    private final ResumeProcessingService processingService;

    @Transactional
    public ResumeSummaryResponse upload(UUID userId, MultipartFile file) {
        validate(file);

        byte[] bytes = readBytes(file);
        String objectKey = storageService.store(bytes, "application/pdf");

        ResumeUpload upload = ResumeUpload.create(
                userId, sanitizeFilenameForDisplay(file.getOriginalFilename()), bytes.length, objectKey);
        upload = resumeUploads.save(upload);
        UUID resumeId = upload.getId();

        // Deferred to afterCommit(), not called directly here. This method is itself
        // @Transactional, so the INSERT above is not yet visible to any other connection
        // until it returns and the transaction commits. Triggering the async worker
        // synchronously here races that commit: the worker runs on the bounded executor's
        // own thread with its own connection, and if it queries findById() before this
        // transaction commits, the row genuinely does not exist yet from its point of
        // view -- which is exactly what happened the first time this was tested (every
        // upload landed in PENDING forever, logging "vanished before processing could
        // start"). afterCommit() guarantees the row is durably visible first.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    triggerProcessing(resumeId);
                }
            });
        } else {
            // No active transaction (e.g. called from a test or a non-transactional
            // context) -- nothing to wait for, so trigger immediately.
            triggerProcessing(resumeId);
        }

        return ResumeMapper.toSummary(upload);
    }

    private void triggerProcessing(UUID resumeId) {
        try {
            processingService.processAsync(resumeId);
        } catch (RejectedExecutionException e) {
            // The bounded executor's queue is full -- a real, if rare, possibility under
            // burst load. The upload itself succeeded (the file is safely in MinIO and the
            // row exists), so mark it FAILED with a clear, retriable reason rather than
            // lose the record or leave it stuck in PENDING forever. This runs after the
            // original transaction already committed, so it is its own fresh save.
            log.warn("Resume processing executor rejected upload {}: queue full", resumeId);
            resumeUploads.findById(resumeId).ifPresent(u -> {
                u.markFailed("The system is currently busy. Please try uploading again in a moment.");
                resumeUploads.save(u);
            });
        }
    }

    @Transactional(readOnly = true)
    public List<ResumeSummaryResponse> listForUser(UUID userId) {
        return resumeUploads.findByUserIdOrderByUploadedAtDesc(userId).stream()
                .map(ResumeMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeDetailResponse get(UUID userId, UUID resumeId) {
        return resumeUploads.findByIdAndUserId(resumeId, userId)
                .map(ResumeMapper::toDetail)
                .orElseThrow(() -> NotFoundException.of("Resume", resumeId));
    }

    /** Package-visible so {@link AtsScoringOrchestrator} can load the resume's own extracted text. */
    ResumeUpload requireOwned(UUID userId, UUID resumeId) {
        return resumeUploads.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> NotFoundException.of("Resume", resumeId));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("File exceeds the 5 MB limit.");
        }

        String declaredType = file.getContentType();
        if (declaredType == null || !declaredType.toLowerCase(Locale.ENGLISH).contains("pdf")) {
            throw new BadRequestException("Only PDF files are accepted.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file.");
        }

        // The declared content type is client-supplied and not trusted; the actual magic
        // bytes are the real check.
        if (bytes.length < PDF_MAGIC.length || !startsWithPdfMagic(bytes)) {
            throw new BadRequestException("The uploaded file is not a valid PDF.");
        }
        return bytes;
    }

    private boolean startsWithPdfMagic(byte[] bytes) {
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** Keeps the filename for display only; it is never used to build a storage path. */
    private String sanitizeFilenameForDisplay(String filename) {
        if (filename == null || filename.isBlank()) {
            return "resume.pdf";
        }
        String base = filename.replaceAll("[\\r\\n]", "").strip();
        return base.length() <= 255 ? base : base.substring(0, 255);
    }
}
