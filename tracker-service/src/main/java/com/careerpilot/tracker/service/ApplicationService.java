package com.careerpilot.tracker.service;

import com.careerpilot.common.error.ApiException;
import com.careerpilot.common.error.ConflictException;
import com.careerpilot.common.error.NotFoundException;
import com.careerpilot.tracker.api.dto.TrackerDtos.ApplicationEventResponse;
import com.careerpilot.tracker.api.dto.TrackerDtos.ApplicationResponse;
import com.careerpilot.tracker.api.dto.TrackerDtos.BoardResponse;
import com.careerpilot.tracker.api.dto.TrackerDtos.CreateApplicationRequest;
import com.careerpilot.tracker.domain.ApplicationStatus;
import com.careerpilot.tracker.domain.JobApplication;
import com.careerpilot.tracker.domain.TransitionSource;
import com.careerpilot.tracker.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final JobApplicationRepository applications;

    @Transactional
    public ApplicationResponse create(UUID userId, CreateApplicationRequest request) {
        // Tracking the same posting twice produces two cards that drift apart as the user
        // updates one and forgets the other, so this is a conflict rather than a silent
        // second card. Only applies when the job came from our corpus and has an id.
        if (request.jobId() != null) {
            applications.findByUserIdAndJobId(userId, request.jobId()).ifPresent(existing -> {
                throw new ConflictException(
                        "You are already tracking this job (status: " + existing.getStatus() + ").");
            });
        }

        JobApplication application = JobApplication.create(
                userId, request.jobId(), request.jobTitle().strip(), request.company().strip(),
                request.applyUrl(), request.location(), request.status(), request.notes());

        return toResponse(applications.save(application));
    }

    /** The Kanban board, grouped server-side into columns. */
    @Transactional(readOnly = true)
    public BoardResponse board(UUID userId) {
        List<JobApplication> all = applications.findByUserIdOrderByUpdatedAtDesc(userId);

        // EnumMap keeps the columns in enum order (WISHLIST -> ... -> WITHDRAWN), so the
        // board renders left-to-right in funnel order without the UI sorting them.
        Map<ApplicationStatus, List<ApplicationResponse>> columns = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            columns.put(status, new java.util.ArrayList<>());
        }
        for (JobApplication application : all) {
            columns.get(application.getStatus()).add(toResponse(application));
        }

        return new BoardResponse(all.size(), columns);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID userId, UUID applicationId) {
        return toResponse(require(userId, applicationId));
    }

    /**
     * Moves a card, rejecting illegal transitions with 409.
     *
     * <p>The check lives here rather than in the entity so the failure surfaces as a proper
     * HTTP status with both states named, instead of an exception from deep in the domain
     * that the client cannot act on.
     */
    @Transactional
    public ApplicationResponse transition(UUID userId, UUID applicationId,
                                          ApplicationStatus target, String note) {
        JobApplication application = require(userId, applicationId);
        applyTransition(application, target, TransitionSource.MANUAL, note);
        return toResponse(applications.save(application));
    }

    /**
     * Shared by the manual and webhook paths so an automated move can never bypass a rule
     * a human move is held to.
     */
    void applyTransition(JobApplication application, ApplicationStatus target,
                         TransitionSource source, String note) {
        ApplicationStatus current = application.getStatus();

        if (current == target) {
            throw new ConflictException("This application is already in " + target + ".");
        }
        if (current.isTerminal()) {
            throw new ConflictException(
                    "%s is a final state; this application cannot be moved again. Create a new entry if you re-applied."
                            .formatted(current));
        }
        if (!current.canTransitionTo(target)) {
            throw new IllegalTransitionException(current, target);
        }

        application.transitionTo(target, source, note);
    }

    @Transactional
    public ApplicationResponse updateNotes(UUID userId, UUID applicationId, String notes) {
        JobApplication application = require(userId, applicationId);
        application.setNotes(notes);
        return toResponse(applications.save(application));
    }

    @Transactional
    public void delete(UUID userId, UUID applicationId) {
        applications.delete(require(userId, applicationId));
    }

    private JobApplication require(UUID userId, UUID applicationId) {
        return applications.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> NotFoundException.of("Application", applicationId));
    }

    static ApplicationResponse toResponse(JobApplication application) {
        return new ApplicationResponse(
                application.getId(),
                application.getJobId(),
                application.getJobTitle(),
                application.getCompany(),
                application.getApplyUrl(),
                application.getLocation(),
                application.getStatus(),
                List.copyOf(application.getStatus().allowedTransitions()),
                application.getNotes(),
                application.getEvents().stream()
                        .map(e -> new ApplicationEventResponse(
                                e.getFromStatus(), e.getToStatus(), e.getSource(),
                                e.getNote(), e.getCreatedAt()))
                        .toList(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    /** 409 naming both states, so the client can explain the refusal without guessing. */
    public static class IllegalTransitionException extends ApiException {
        public IllegalTransitionException(ApplicationStatus from, ApplicationStatus to) {
            super(org.springframework.http.HttpStatus.CONFLICT, "Illegal status transition",
                    "Cannot move an application from %s to %s. Allowed from %s: %s."
                            .formatted(from, to, from,
                                    from.allowedTransitions().isEmpty()
                                            ? "nothing (final state)"
                                            : from.allowedTransitions()));
        }
    }
}
