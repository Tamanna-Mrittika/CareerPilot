package com.careerpilot.tracker.api;

import com.careerpilot.common.security.CurrentUser;
import com.careerpilot.tracker.api.dto.TrackerDtos.ApplicationResponse;
import com.careerpilot.tracker.api.dto.TrackerDtos.BoardResponse;
import com.careerpilot.tracker.api.dto.TrackerDtos.CreateApplicationRequest;
import com.careerpilot.tracker.api.dto.TrackerDtos.TransitionRequest;
import com.careerpilot.tracker.api.dto.TrackerDtos.UpdateNotesRequest;
import com.careerpilot.tracker.domain.ApplicationStatus;
import com.careerpilot.tracker.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The Kanban board. Every route is scoped to the caller's own applications, resolved from
 * the validated JWT -- never from a user id in the path or body.
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Application tracker")
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @Operation(summary = "The caller's Kanban board, grouped into columns",
            description = """
                    Columns are returned in funnel order (WISHLIST -> APPLIED -> INTERVIEWING
                    -> OFFER, plus the terminal REJECTED and WITHDRAWN). Each card carries
                    `allowedTransitions`, so the UI can grey out illegal drop targets rather
                    than letting the user attempt a move the server will refuse.
                    """)
    public BoardResponse board() {
        return applicationService.board(CurrentUser.requireId());
    }

    @PostMapping
    @Operation(summary = "Start tracking an application",
            description = "Tracking the same jobId twice returns 409 rather than creating a duplicate card.")
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.create(CurrentUser.requireId(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One application, including its full status history")
    public ApplicationResponse get(@PathVariable UUID id) {
        return applicationService.get(CurrentUser.requireId(), id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move a card to a new column",
            description = """
                    Enforces the state machine: an illegal move (e.g. INTERVIEWING back to
                    WISHLIST, or any move out of a terminal state) returns 409 naming both
                    states and what was allowed.
                    """)
    public ApplicationResponse transition(@PathVariable UUID id,
                                          @Valid @RequestBody TransitionRequest request) {
        return applicationService.transition(CurrentUser.requireId(), id, request.status(), request.note());
    }

    @PatchMapping("/{id}/notes")
    @Operation(summary = "Update the free-text notes on a card")
    public ApplicationResponse updateNotes(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateNotesRequest request) {
        return applicationService.updateNotes(CurrentUser.requireId(), id, request.notes());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Stop tracking an application")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        applicationService.delete(CurrentUser.requireId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statuses")
    @Operation(summary = "The state machine itself",
            description = "Every status and what it may transition to. Lets a client build the board without hardcoding the rules.")
    public Map<ApplicationStatus, Object> statuses() {
        Map<ApplicationStatus, Object> machine = new java.util.EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            machine.put(status, Map.of(
                    "allowedTransitions", status.allowedTransitions(),
                    "terminal", status.isTerminal()));
        }
        return machine;
    }
}
