package com.careerpilot.resume.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Document-level understanding of the resume text: which expected sections are present,
 * and how many years of experience the date ranges in the text actually add up to.
 */
@Component
@Slf4j
public class ResumeSectionAnalyzer {

    public record MissingSection(String sectionName, String message) {
    }

    /** Header keyword -> what to call it in feedback. Matched case-insensitively, line-anchored. */
    private static final Map<String, String> EXPECTED_SECTIONS = Map.of(
            "(experience|work\\s+history|employment)", "Experience",
            "education", "Education",
            "skills", "Skills"
    );

    public List<MissingSection> findMissingSections(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ENGLISH);
        List<MissingSection> missing = new ArrayList<>();

        for (Map.Entry<String, String> entry : EXPECTED_SECTIONS.entrySet()) {
            Pattern headerPattern = Pattern.compile("(?m)^\\s*" + entry.getKey() + "\\s*$",
                    Pattern.CASE_INSENSITIVE);
            if (!headerPattern.matcher(lower).find()) {
                missing.add(new MissingSection(entry.getValue(),
                        "No \"" + entry.getValue() + "\" section header was detected. ATS "
                                + "systems and recruiters both rely on standard section "
                                + "headers to parse a resume correctly."));
            }
        }
        return missing;
    }

    // ---- date-range experience inference ------------------------------------------------

    private static final Map<String, Integer> MONTH_NUMBERS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12)
    );

    /**
     * Matches "Jan 2022", "January 2022 - Present", "2021 - 2023", "03/2020 to 06/2022" and
     * similar. When only a year is present (no month), the start of a range is treated as
     * January and the end as December of that year -- a deliberate, documented
     * simplification rather than an attempt at exact-day precision the source text does
     * not actually contain.
     */
    private static final Pattern DATE_RANGE = Pattern.compile(
            "(?i)(?<startMonth>jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|"
                    + "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)?"
                    + "\\.?\\s*(?<startYear>(?:19|20)\\d{2})"
                    + "\\s*(?:-|–|—|to)\\s*"
                    + "(?<endToken>present|current|now|"
                    + "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|"
                    + "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
                    + "\\.?\\s*(?:19|20)\\d{2}|(?:19|20)\\d{2})"
                    + ")"
    );

    /**
     * Matches a section header line, used to bound where work-experience date ranges are
     * read from.
     */
    private static final Pattern SECTION_HEADER = Pattern.compile(
            "(?im)^\\s*(experience|work\\s+history|employment|education|skills|projects?|"
                    + "certifications?|summary|objective|awards?|publications?|languages?)\\s*:?\\s*$");

    private static final Pattern EXPERIENCE_HEADER = Pattern.compile(
            "(?i)^\\s*(experience|work\\s+history|employment)\\s*:?\\s*$");

    /**
     * @return inferred years of experience, or null if no usable date ranges were found.
     *
     * <p>Only reads date ranges from within the experience section. Scanning the whole
     * document double-counts education ("2015 - 2019" for a degree is not work experience)
     * and inflates the total badly -- the first real test of this produced 12 years for a
     * resume describing about 7.
     */
    public Integer inferYearsExperience(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String experienceSection = extractExperienceSection(text);
        if (experienceSection == null) {
            // No recognisable experience section: fall back to the whole document rather
            // than returning nothing, but this is the less reliable path.
            experienceSection = text;
        }

        List<long[]> periods = new ArrayList<>();
        Matcher matcher = DATE_RANGE.matcher(experienceSection);
        while (matcher.find()) {
            LocalDate start = toStartOfPeriod(matcher.group("startMonth"), matcher.group("startYear"));
            LocalDate end = toEndOfPeriod(matcher.group("endToken"));
            if (start != null && end != null && !end.isBefore(start)) {
                periods.add(new long[]{start.toEpochDay(), end.toEpochDay()});
            }
        }

        if (periods.isEmpty()) {
            return null;
        }
        return (int) Math.round(mergeOverlappingDays(periods) / 365.25);
    }

    /**
     * Returns the text between the "Experience" header and the next section header, or
     * null when no experience header is present.
     */
    private String extractExperienceSection(String text) {
        String[] lines = text.split("\\R");
        int start = -1;

        for (int i = 0; i < lines.length; i++) {
            if (EXPERIENCE_HEADER.matcher(lines[i]).matches()) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            return null;
        }

        StringBuilder section = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (SECTION_HEADER.matcher(lines[i]).matches()) {
                break; // hit the next section (Education, Skills, ...)
            }
            section.append(lines[i]).append('\n');
        }
        return section.toString();
    }

    private LocalDate toStartOfPeriod(String monthText, String yearText) {
        int year = Integer.parseInt(yearText);
        if (year > Year.now().getValue() + 1) {
            return null; // implausible, likely a mis-match (e.g. a phone number caught as a year)
        }
        Integer month = resolveMonth(monthText);
        return LocalDate.of(year, month == null ? 1 : month, 1);
    }

    private LocalDate toEndOfPeriod(String endToken) {
        String normalized = endToken.toLowerCase(Locale.ENGLISH).strip();
        if (normalized.equals("present") || normalized.equals("current") || normalized.equals("now")) {
            return LocalDate.now();
        }

        Matcher yearMatcher = Pattern.compile("(19|20)\\d{2}").matcher(normalized);
        if (!yearMatcher.find()) {
            return null;
        }
        int year = Integer.parseInt(yearMatcher.group());
        Integer month = resolveMonth(normalized);
        // Unknown month at the end of a range: assume mid-year (June) rather than December.
        // Pairing a January start assumption with a December end assumption systematically
        // rounds every year-only range UP by a full year, which compounds fast across
        // several roles. Mid-year keeps the expected error centred near zero instead.
        int resolvedMonth = month == null ? 6 : month;
        return LocalDate.of(year, resolvedMonth, 1).withDayOfMonth(
                LocalDate.of(year, resolvedMonth, 1).lengthOfMonth());
    }

    private Integer resolveMonth(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ENGLISH).strip();
        for (Map.Entry<String, Integer> entry : MONTH_NUMBERS.entrySet()) {
            if (lower.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Same overlapping-interval-merge approach as profile-service's
     * {@code Profile.recalculateYearsExperience()} -- two concurrent roles should count
     * once, not twice. Duplicated rather than shared: it is a small, self-contained
     * algorithm, and pulling it into a shared library across two services would cost more
     * in coupling than it saves in line count.
     */
    private long mergeOverlappingDays(List<long[]> periods) {
        periods.sort((a, b) -> Long.compare(a[0], b[0]));

        long totalDays = 0;
        long currentStart = periods.get(0)[0];
        long currentEnd = periods.get(0)[1];

        for (int i = 1; i < periods.size(); i++) {
            long[] period = periods.get(i);
            if (period[0] <= currentEnd) {
                currentEnd = Math.max(currentEnd, period[1]);
            } else {
                totalDays += currentEnd - currentStart;
                currentStart = period[0];
                currentEnd = period[1];
            }
        }
        totalDays += currentEnd - currentStart;
        return totalDays;
    }
}
