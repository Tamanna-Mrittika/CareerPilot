package com.careerpilot.tracker.api.dto;

import com.careerpilot.tracker.domain.ApplicationStatus;
import com.careerpilot.tracker.domain.TransitionSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TrackerDtos {

    private TrackerDtos() {
    }

    // ---- requests ----------------------------------------------------------

    public record CreateApplicationRequest(
            /** Optional soft link to job-service; null when tracking a role found elsewhere. */
            UUID jobId,
            @NotBlank @Size(max = 500) String jobTitle,
            @NotBlank @Size(max = 300) String company,
            @Size(max = 1000) String applyUrl,
            @Size(max = 300) String location,
            /** Defaults to WISHLIST when omitted. */
            ApplicationStatus status,
            @Size(max = 4000) String notes) {
    }

    public record TransitionRequest(
            @NotNull ApplicationStatus status,
            @Size(max = 500) String note) {
    }

    public record UpdateNotesRequest(
            @Size(max = 4000) String notes) {
    }

    /** Payload shape for the inbound email webhook and its ADMIN-only simulate twin. */
    public record EmailWebhookRequest(
            @Size(max = 320) String from,
            @Size(max = 500) String subject,
            @Size(max = 20000) String body,
            Instant receivedAt) {
    }

    // ---- responses ---------------------------------------------------------

    public record ApplicationEventResponse(
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            TransitionSource source,
            String note,
            Instant createdAt) {
    }

    public record ApplicationResponse(
            UUID id,
            UUID jobId,
            String jobTitle,
            String company,
            String applyUrl,
            String location,
            ApplicationStatus status,
            /** Where this card may legally move next -- lets the UI grey out illegal drops. */
            List<ApplicationStatus> allowedTransitions,
            String notes,
            List<ApplicationEventResponse> events,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * The board itself: applications grouped by column, so the UI does not have to
     * regroup a flat list and cannot disagree with the server about which column a card
     * belongs in.
     */
    public record BoardResponse(
            int total,
            Map<ApplicationStatus, List<ApplicationResponse>> columns) {
    }

    /** What the webhook did, so the caller (and the demo) can see the classification. */
    public record WebhookResultResponse(
            boolean matched,
            String outcome,
            UUID applicationId,
            ApplicationStatus previousStatus,
            ApplicationStatus newStatus,
            String matchedPhrase) {
    }
}
