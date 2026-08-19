package com.careerpilot.resume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One rule-based piece of feedback, always tied to the specific text that triggered it
 * ({@code evidence}) -- traceability is the point: every suggestion must be defensible as
 * "here is the exact line, here is why it was flagged."
 */
@Entity
@Table(name = "suggestion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suggestion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_upload_id", nullable = false)
    private ResumeUpload resumeUpload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, length = 500)
    private String message;

    /** The offending line/bullet verbatim, or null for document-level findings like MISSING_SECTION. */
    @Column(length = 500)
    private String evidence;

    public static Suggestion create(ResumeUpload resumeUpload, SuggestionCategory category,
                                    Severity severity, String message, String evidence) {
        Suggestion suggestion = new Suggestion();
        suggestion.id = UUID.randomUUID();
        suggestion.resumeUpload = resumeUpload;
        suggestion.category = category;
        suggestion.severity = severity;
        suggestion.message = message;
        suggestion.evidence = evidence;
        return suggestion;
    }
}
