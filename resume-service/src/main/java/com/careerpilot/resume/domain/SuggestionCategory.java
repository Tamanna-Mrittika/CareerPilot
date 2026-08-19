package com.careerpilot.resume.domain;

/** Rule-based bullet/prose feedback categories -- each one maps to exactly one detection rule. */
public enum SuggestionCategory {
    WEAK_VERB,
    UNQUANTIFIED_BULLET,
    PASSIVE_VOICE,
    BULLET_TOO_LONG,
    FIRST_PERSON_PRONOUN,
    MISSING_SECTION
}
