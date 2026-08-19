package com.careerpilot.profile.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One canonical skill in the shared taxonomy.
 *
 * <p>{@code aliases} is what makes free-text resume parsing work: a CV saying "JS", "ES6"
 * or "Javascript" must all resolve to the single canonical "JavaScript" row, otherwise the
 * same candidate matches inconsistently depending on how they happened to write it.
 * resume-service builds its Aho-Corasick automaton from exactly this alias set.
 */
@Entity
@Table(name = "skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

    @Id
    private UUID id;

    /** Display form, e.g. "PostgreSQL". */
    @Column(nullable = false, unique = true)
    private String name;

    /** Lower-cased, hyphenated lookup key, e.g. "postgresql". */
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String category;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "skill_alias", joinColumns = @JoinColumn(name = "skill_id"))
    @Column(name = "alias", nullable = false)
    private Set<String> aliases = new LinkedHashSet<>();
}
