package com.careerpilot.resume.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One uploaded resume and everything derived from it.
 *
 * <p>The PDF bytes themselves are never here and never in this database -- they live in
 * MinIO, referenced by {@code storageObjectKey}. Keeping large binary payloads out of
 * Postgres is the point: this table stays small and fast to query regardless of how many
 * multi-megabyte PDFs accumulate.
 */
@Entity
@Table(name = "resume_upload")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeUpload {

    @Id
    private UUID id;

    /** Subject claim of the JWT that uploaded this resume. No foreign key -- see profile-service's Profile for why. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** MinIO object key. Derived from a UUID, never from the user-supplied filename. */
    @Column(name = "storage_object_key", nullable = false, unique = true)
    private String storageObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResumeStatus status = ResumeStatus.PENDING;

    /** Populated only when status is FAILED. */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** Raw extracted text, kept for on-demand ATS scoring against any job without re-parsing the PDF. */
    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    /**
     * Years of experience inferred from date ranges found in the resume text itself.
     * Deliberately independent of profile-service's own {@code years_experience} (which
     * comes from structured entries the user typed in) -- the two can legitimately
     * disagree, and that gap is itself useful signal.
     */
    @Column(name = "inferred_years_experience")
    private Integer inferredYearsExperience;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @OneToMany(mappedBy = "resumeUpload", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ExtractedSkill> extractedSkills = new ArrayList<>();

    @OneToMany(mappedBy = "resumeUpload", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AtsCheck> atsChecks = new ArrayList<>();

    @OneToMany(mappedBy = "resumeUpload", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Suggestion> suggestions = new ArrayList<>();

    public static ResumeUpload create(UUID userId, String originalFilename, long sizeBytes, String storageObjectKey) {
        ResumeUpload upload = new ResumeUpload();
        upload.id = UUID.randomUUID();
        upload.userId = userId;
        upload.originalFilename = originalFilename;
        upload.sizeBytes = sizeBytes;
        upload.storageObjectKey = storageObjectKey;
        upload.status = ResumeStatus.PENDING;
        upload.uploadedAt = Instant.now();
        return upload;
    }

    public void markProcessing() {
        this.status = ResumeStatus.PROCESSING;
    }

    public void markCompleted() {
        this.status = ResumeStatus.COMPLETED;
        this.processedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = ResumeStatus.FAILED;
        // Truncated defensively: an unexpected exception's message could exceed the column.
        this.errorMessage = reason == null ? "Unknown error"
                : reason.substring(0, Math.min(reason.length(), 2000));
        this.processedAt = Instant.now();
    }
}
