package com.careerpilot.tracker.domain;

/**
 * What caused a status change. Recorded on every event so an auto-transition is always
 * distinguishable from a deliberate user action -- important when a classifier gets it
 * wrong and the user wants to know why their card moved on its own.
 */
public enum TransitionSource {
    /** The user dragged the card / called the API directly. */
    MANUAL,
    /** Inferred from an inbound email webhook by EmailClassifier. */
    EMAIL_WEBHOOK,
    /** Created by the initial save. */
    INITIAL
}
