package com.careerpilot.profile.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
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
 * A candidate's profile, keyed one-to-one to an identity-service user.
 *
 * <p>{@code userId} is stored as a plain UUID with no foreign key, because the users table
 * lives in another service's schema that this service has no grants on. That is the
 * intended trade-off of service-owned data: referential integrity across the boundary is
 * the application's job, not the database's.
 */
@Entity
@Table(name = "profile")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

    @Id
    private UUID id;

    /** Subject claim of the JWT. Unique: one profile per user. */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String headline;

    @Column(length = 4000)
    private String summary;

    private String email;
    private String phone;

    @Column(name = "location_city")
    private String locationCity;

    @Column(name = "location_country")
    private String locationCountry;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_preference", nullable = false)
    private RemotePreference remotePreference = RemotePreference.ANY;

    /**
     * Denormalised total years of experience. Derived from {@link Experience} rows by
     * {@code recalculateYearsExperience()} rather than trusted from the client, so the
     * number matching-service scores against cannot be inflated by hand.
     */
    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("startDate DESC")
    private List<Education> education = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("startDate DESC")
    private List<Experience> experience = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProfileSkill> skills = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static Profile create(UUID userId, String fullName, String email) {
        Profile profile = new Profile();
        profile.id = UUID.randomUUID();
        profile.userId = userId;
        profile.fullName = fullName;
        profile.email = email;
        profile.remotePreference = RemotePreference.ANY;
        profile.createdAt = Instant.now();
        profile.updatedAt = Instant.now();
        return profile;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void addEducation(Education entry) {
        entry.setProfile(this);
        this.education.add(entry);
    }

    public void addExperience(Experience entry) {
        entry.setProfile(this);
        this.experience.add(entry);
        recalculateYearsExperience();
    }

    /**
     * Recomputes total experience by <em>merging overlapping periods</em> rather than
     * summing durations. Two concurrent roles (a job plus a freelance contract) are three
     * years of experience, not six -- naive summation is a common way these numbers end up
     * quietly wrong.
     */
    public void recalculateYearsExperience() {
        List<long[]> periods = experience.stream()
                .filter(e -> e.getStartDate() != null)
                .map(e -> new long[]{
                        e.getStartDate().toEpochDay(),
                        (e.getEndDate() != null ? e.getEndDate() : java.time.LocalDate.now()).toEpochDay()})
                .filter(p -> p[1] > p[0])
                .sorted((a, b) -> Long.compare(a[0], b[0]))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        long totalDays = 0;
        long currentStart = Long.MIN_VALUE;
        long currentEnd = Long.MIN_VALUE;

        for (long[] period : periods) {
            if (currentEnd == Long.MIN_VALUE) {
                currentStart = period[0];
                currentEnd = period[1];
            } else if (period[0] <= currentEnd) {
                currentEnd = Math.max(currentEnd, period[1]);   // overlap: extend
            } else {
                totalDays += currentEnd - currentStart;          // gap: bank and restart
                currentStart = period[0];
                currentEnd = period[1];
            }
        }
        if (currentEnd != Long.MIN_VALUE) {
            totalDays += currentEnd - currentStart;
        }

        this.yearsExperience = (int) Math.round(totalDays / 365.25);
    }
}
