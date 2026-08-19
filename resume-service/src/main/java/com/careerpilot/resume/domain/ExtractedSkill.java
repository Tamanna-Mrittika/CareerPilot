package com.careerpilot.resume.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One skill the Aho-Corasick matcher found in the resume text.
 *
 * <p>Name and category are denormalised from profile-service's taxonomy at extraction
 * time rather than joined live, because this row is a historical record of what was found
 * in <em>this</em> resume -- it should keep reading the same way even if the taxonomy
 * entry is later renamed or recategorised.
 */
@Entity
@Table(name = "extracted_skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractedSkill {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_upload_id", nullable = false)
    private ResumeUpload resumeUpload;

    @Column(name = "skill_slug", nullable = false)
    private String skillSlug;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "category", nullable = false)
    private String category;

    /** How many times a match for this skill (any of its aliases) occurred in the text. */
    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    public static ExtractedSkill create(ResumeUpload resumeUpload, String slug, String name,
                                        String category, int occurrenceCount) {
        ExtractedSkill skill = new ExtractedSkill();
        skill.id = UUID.randomUUID();
        skill.resumeUpload = resumeUpload;
        skill.skillSlug = slug;
        skill.skillName = name;
        skill.category = category;
        skill.occurrenceCount = occurrenceCount;
        return skill;
    }
}
