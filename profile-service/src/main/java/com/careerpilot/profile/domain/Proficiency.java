package com.careerpilot.profile.domain;

/**
 * Self-assessed command of a skill.
 *
 * <p>{@code weight} feeds matching-service's fit score: claiming a skill at BEGINNER should
 * not count the same as EXPERT when a job lists it as required. Kept here rather than in
 * the matcher so the scale has exactly one definition.
 */
public enum Proficiency {

    BEGINNER(0.4),
    INTERMEDIATE(0.7),
    ADVANCED(0.9),
    EXPERT(1.0);

    private final double weight;

    Proficiency(double weight) {
        this.weight = weight;
    }

    public double weight() {
        return weight;
    }
}
