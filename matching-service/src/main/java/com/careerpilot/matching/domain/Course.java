package com.careerpilot.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One free learning resource mapped to a skill.
 *
 * <p>Seeded entirely by Flyway migration, read-only at runtime -- the skill-gap feature
 * has no external runtime dependency (no scraping a course marketplace, no paid API).
 * Deliberately not exhaustive: only skills likely to actually appear as gaps in real job
 * postings have entries; a skill with no course row simply is not recommended, which is
 * a better failure mode than a broken or stale link.
 */
@Entity
@Table(name = "course")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    private UUID id;

    /** Matches profile-service's skill slug -- the join key between the two services' independent schemas. */
    @Column(name = "skill_slug", nullable = false)
    private String skillSlug;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 500)
    private String url;
}
