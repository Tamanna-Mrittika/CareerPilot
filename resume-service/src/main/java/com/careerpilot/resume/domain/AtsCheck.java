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

/** One structural finding from PDFBox analysis, persisted so a poll never re-parses the PDF. */
@Entity
@Table(name = "ats_check")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AtsCheck {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_upload_id", nullable = false)
    private ResumeUpload resumeUpload;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    private AtsCheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false, length = 500)
    private String message;

    public static AtsCheck create(ResumeUpload resumeUpload, AtsCheckType type, Severity severity, String message) {
        AtsCheck check = new AtsCheck();
        check.id = UUID.randomUUID();
        check.resumeUpload = resumeUpload;
        check.checkType = type;
        check.severity = severity;
        check.message = message;
        return check;
    }
}
