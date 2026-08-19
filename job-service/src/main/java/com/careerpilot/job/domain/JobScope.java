package com.careerpilot.job.domain;

/**
 * Which kind of opening a search is after.
 *
 * <p>These are genuinely different job hunts and the product keeps them on separate pages.
 * A candidate in Dhaka searching LOCAL wants employers they can commute to; searching
 * REMOTE they are competing globally, where salary, timezone and hiring bar all differ.
 * Mixing the two into one list makes both worse, because a hundred remote listings drown
 * out the handful of local ones.
 */
public enum JobScope {

    /** On-site or hybrid roles tied to a city/country. */
    LOCAL,

    /** Remote roles, location-independent. */
    REMOTE,

    /** Both, for the rare case a caller genuinely wants everything. */
    ALL
}
