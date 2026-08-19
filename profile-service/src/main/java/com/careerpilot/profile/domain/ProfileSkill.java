package com.careerpilot.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Join between a profile and a canonical skill, carrying how well the candidate knows it. */
@Entity
@Table(name = "profile_skill",
        uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "skill_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileSkill {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Proficiency proficiency = Proficiency.INTERMEDIATE;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    /**
     * True when the skill was inferred from an uploaded resume rather than entered by hand.
     * Kept so the UI can ask the user to confirm inferred skills -- an unconfirmed guess
     * from a PDF should not silently carry the same authority as a deliberate claim.
     */
    @Column(name = "extracted_from_resume", nullable = false)
    private boolean extractedFromResume;

    public static ProfileSkill create(Profile profile, Skill skill, Proficiency proficiency,
                                      Integer yearsExperience, boolean extractedFromResume) {
        ProfileSkill profileSkill = new ProfileSkill();
        profileSkill.id = UUID.randomUUID();
        profileSkill.profile = profile;
        profileSkill.skill = skill;
        profileSkill.proficiency = proficiency == null ? Proficiency.INTERMEDIATE : proficiency;
        profileSkill.yearsExperience = yearsExperience;
        profileSkill.extractedFromResume = extractedFromResume;
        return profileSkill;
    }
}
