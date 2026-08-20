package com.careerpilot.tracker.domain;

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
 * One tracked application: a card on the Kanban board.
 *
 * <p>Named JobApplication, not Application, purely to avoid colliding with Spring's own
 * Application types in a codebase where that name appears constantly.
 *
 * <p>Job title and company are copied in rather than referenced from job-service. Two
 * reasons: a user can track a role that was never in our corpus (found on a friend's
 * recommendation, a company careers page), and a posting that gets deleted upstream must
 * not blank out the user's own record of having applied to it. {@code jobId} is kept when
 * known, as a soft link for deep-linking back to the listing while it still exists.
 */
@Entity
@Table(name = "job_application")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Soft link to job-service. Null when the user tracked a role found elsewhere. */
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(nullable = false)
    private String company;

    @Column(name = "apply_url", length = 1000)
    private String applyUrl;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.WISHLIST;

    @Column(length = 4000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<ApplicationEvent> events = new ArrayList<>();

    public static JobApplication create(UUID userId, UUID jobId, String jobTitle, String company,
                                        String applyUrl, String location, ApplicationStatus initialStatus,
                                        String notes) {
        JobApplication application = new JobApplication();
        application.id = UUID.randomUUID();
        application.userId = userId;
        application.jobId = jobId;
        application.jobTitle = jobTitle;
        application.company = company;
        application.applyUrl = applyUrl;
        application.location = location;
        application.status = initialStatus == null ? ApplicationStatus.WISHLIST : initialStatus;
        application.notes = notes;
        application.createdAt = Instant.now();
        application.updatedAt = Instant.now();
        application.events.add(ApplicationEvent.create(
                application, null, application.status, TransitionSource.INITIAL, "Application created"));
        return application;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /**
     * Applies a status change and records it. Callers must have already validated the
     * transition -- see {@code ApplicationService}, which owns that check so the illegal
     * case produces a proper 409 rather than an exception from deep in the entity.
     */
    public void transitionTo(ApplicationStatus target, TransitionSource source, String note) {
        ApplicationStatus previous = this.status;
        this.status = target;
        this.updatedAt = Instant.now();
        this.events.add(ApplicationEvent.create(this, previous, target, source, note));
    }
}
