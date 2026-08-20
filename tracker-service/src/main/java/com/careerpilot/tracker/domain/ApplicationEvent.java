package com.careerpilot.tracker.domain;

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

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable audit entry for one status change.
 *
 * <p>The board shows current state; this shows how it got there. That matters most for
 * webhook-driven moves: when a card advances on its own, the user needs to see which
 * email caused it and what the classifier read, otherwise an automated transition is
 * indistinguishable from a bug.
 */
@Entity
@Table(name = "application_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private JobApplication application;

    /** Null on the initial creation event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ApplicationStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransitionSource source;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    static ApplicationEvent create(JobApplication application, ApplicationStatus from,
                                   ApplicationStatus to, TransitionSource source, String note) {
        ApplicationEvent event = new ApplicationEvent();
        event.id = UUID.randomUUID();
        event.application = application;
        event.fromStatus = from;
        event.toStatus = to;
        event.source = source;
        event.note = note == null ? null : note.substring(0, Math.min(note.length(), 500));
        event.createdAt = Instant.now();
        return event;
    }
}
