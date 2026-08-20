package com.careerpilot.tracker.domain;

import java.util.Set;

/**
 * Kanban columns, modelled as an explicit state machine rather than a free-text field.
 *
 * <p>The legal transitions live on the enum itself, so the rules cannot drift apart from
 * the states they govern. An application only ever moves forward through the funnel, or
 * sideways into a terminal outcome -- there is no path from INTERVIEWING back to WISHLIST,
 * because that did not happen in reality and silently allowing it would let the board tell
 * a story the user never lived.
 *
 * <p>{@link #REJECTED} and {@link #WITHDRAWN} are terminal. Reaching a terminal state is
 * the end of that application's life; a user re-applying to the same role creates a new
 * card, which keeps the history of the first attempt intact.
 */
public enum ApplicationStatus {

    /** Saved but not yet applied to. */
    WISHLIST,

    APPLIED,

    INTERVIEWING,

    /** An offer was received. Terminal unless declined, which moves to WITHDRAWN. */
    OFFER,

    /** The employer said no. Terminal. */
    REJECTED,

    /** The candidate pulled out or declined an offer. Terminal. */
    WITHDRAWN;

    /**
     * @return the states this one may legally move to. An empty set means terminal.
     */
    public Set<ApplicationStatus> allowedTransitions() {
        return switch (this) {
            // Straight to REJECTED from WISHLIST is legal: the posting can close, or the
            // candidate can be rejected before they formally applied via a referral.
            case WISHLIST -> Set.of(APPLIED, REJECTED, WITHDRAWN);
            // OFFER directly from APPLIED is unusual but real (small companies skip formal
            // interview rounds), so it is permitted rather than forced through INTERVIEWING.
            case APPLIED -> Set.of(INTERVIEWING, OFFER, REJECTED, WITHDRAWN);
            case INTERVIEWING -> Set.of(OFFER, REJECTED, WITHDRAWN);
            // An offer can still be declined.
            case OFFER -> Set.of(WITHDRAWN);
            case REJECTED, WITHDRAWN -> Set.of();
        };
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
