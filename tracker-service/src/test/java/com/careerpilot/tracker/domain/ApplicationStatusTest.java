package com.careerpilot.tracker.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The state machine is the reason this service exists rather than a status string column,
 * so its rules are worth pinning explicitly.
 */
class ApplicationStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "WISHLIST,     APPLIED",
            "WISHLIST,     REJECTED",
            "WISHLIST,     WITHDRAWN",
            "APPLIED,      INTERVIEWING",
            "APPLIED,      REJECTED",
            "APPLIED,      OFFER",
            "APPLIED,      WITHDRAWN",
            "INTERVIEWING, OFFER",
            "INTERVIEWING, REJECTED",
            "INTERVIEWING, WITHDRAWN",
            "OFFER,        WITHDRAWN"
    })
    void legalTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @CsvSource({
            // Backwards through the funnel: these did not happen in reality.
            "APPLIED,      WISHLIST",
            "INTERVIEWING, WISHLIST",
            "INTERVIEWING, APPLIED",
            "OFFER,        WISHLIST",
            "OFFER,        APPLIED",
            "OFFER,        INTERVIEWING",
            // Out of a terminal state.
            "REJECTED,     APPLIED",
            "REJECTED,     INTERVIEWING",
            "REJECTED,     WITHDRAWN",
            "WITHDRAWN,    APPLIED",
            "WITHDRAWN,    REJECTED"
    })
    void illegalTransitions(ApplicationStatus from, ApplicationStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    @DisplayName("REJECTED and WITHDRAWN are the only terminal states")
    void terminalStates() {
        assertThat(ApplicationStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.WITHDRAWN.isTerminal()).isTrue();

        assertThat(ApplicationStatus.WISHLIST.isTerminal()).isFalse();
        assertThat(ApplicationStatus.APPLIED.isTerminal()).isFalse();
        assertThat(ApplicationStatus.INTERVIEWING.isTerminal()).isFalse();
        assertThat(ApplicationStatus.OFFER.isTerminal()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    @DisplayName("no state can transition to itself")
    void noSelfTransition(ApplicationStatus status) {
        // Self-transitions are rejected in ApplicationService with a distinct "already in
        // that state" message; allowing one here would let a redundant move write a
        // misleading audit event claiming the card changed.
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    @DisplayName("every non-terminal state can reach a terminal one")
    void everyActiveStateCanBeClosed(ApplicationStatus status) {
        if (status.isTerminal()) {
            return;
        }
        // Otherwise a card could get permanently stuck mid-funnel with no way to close it.
        assertThat(status.allowedTransitions())
                .anyMatch(ApplicationStatus::isTerminal);
    }
}
