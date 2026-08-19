package com.careerpilot.job.domain;

import java.util.Locale;

/** Normalised employment type; each provider spells these differently. */
public enum EmploymentType {

    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP,
    TEMPORARY,
    OTHER;

    /**
     * Maps a provider's free-text label onto the enum. Falls back to OTHER rather than
     * throwing: an unrecognised label is not a reason to discard an otherwise good posting.
     */
    public static EmploymentType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String v = raw.toLowerCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
        if (v.contains("full")) return FULL_TIME;
        if (v.contains("part")) return PART_TIME;
        if (v.contains("contract") || v.contains("freelance")) return CONTRACT;
        if (v.contains("intern")) return INTERNSHIP;
        if (v.contains("temp")) return TEMPORARY;
        return OTHER;
    }
}
