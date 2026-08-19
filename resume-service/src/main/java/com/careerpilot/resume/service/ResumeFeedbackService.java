package com.careerpilot.resume.service;

import com.careerpilot.resume.domain.Severity;
import com.careerpilot.resume.domain.SuggestionCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based bullet-level feedback. Every rule is a single, named, explainable check --
 * intentionally not a machine-learned style model, so a user can always see exactly why a
 * line was flagged rather than trust an opaque score.
 *
 * <p>Each rule caps how many lines it reports ({@link #MAX_EVIDENCE_PER_CATEGORY}): a
 * resume with fifteen unquantified bullets does not need fifteen near-identical
 * suggestions, and capping keeps the response focused on the highest-value fixes.
 */
@Component
public class ResumeFeedbackService {

    private static final int MAX_EVIDENCE_PER_CATEGORY = 8;
    private static final int MIN_LINE_LENGTH_TO_CONSIDER = 15;

    public record Finding(SuggestionCategory category, Severity severity, String message, String evidence) {
    }

    private static final Set<String> WEAK_OPENERS = Set.of(
            "responsible for", "helped with", "worked on", "involved in", "assisted with",
            "duties included", "in charge of", "participated in", "worked with", "tasked with",
            "helped to", "was responsible for"
    );

    private static final Pattern HAS_METRIC = Pattern.compile("[0-9%$]");
    private static final Pattern PASSIVE_VOICE = Pattern.compile(
            "(?i)\\b(was|were|been|is|are|be)\\s+\\w+ed\\b");
    private static final Pattern FIRST_PERSON = Pattern.compile(
            "(?i)\\b(i|me|my|myself)\\b");
    private static final Pattern LOOKS_LIKE_HEADER_OR_DATE = Pattern.compile(
            "(?i)^(experience|education|skills|work\\s+history|employment|projects?|"
                    + "certifications?|summary|objective)\\s*$|\\b(19|20)\\d{2}\\b");

    private static final int TOO_LONG_CHAR_THRESHOLD = 220;
    private static final int TOO_LONG_WORD_THRESHOLD = 40;

    public List<Finding> analyze(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<Finding> weakVerb = new ArrayList<>();
        List<Finding> unquantified = new ArrayList<>();
        List<Finding> passive = new ArrayList<>();
        List<Finding> tooLong = new ArrayList<>();
        List<Finding> firstPerson = new ArrayList<>();

        for (String rawLine : text.split("\\R")) {
            String line = stripBulletMarker(rawLine.strip());
            if (line.length() < MIN_LINE_LENGTH_TO_CONSIDER) {
                continue;
            }

            String lower = line.toLowerCase(Locale.ENGLISH);

            if (weakVerb.size() < MAX_EVIDENCE_PER_CATEGORY) {
                WEAK_OPENERS.stream()
                        .filter(lower::startsWith)
                        .findFirst()
                        .ifPresent(phrase -> weakVerb.add(new Finding(
                                SuggestionCategory.WEAK_VERB, Severity.WARNING,
                                "Starts with a weak, passive phrase (\"" + phrase + "\"); "
                                        + "lead with a strong action verb instead (e.g. \"Built\", "
                                        + "\"Led\", \"Reduced\").",
                                line)));
            }

            boolean looksLikeAccomplishment = line.length() > 40
                    && !LOOKS_LIKE_HEADER_OR_DATE.matcher(line).find();
            if (looksLikeAccomplishment && !HAS_METRIC.matcher(line).find()
                    && unquantified.size() < MAX_EVIDENCE_PER_CATEGORY) {
                unquantified.add(new Finding(
                        SuggestionCategory.UNQUANTIFIED_BULLET, Severity.INFO,
                        "No number, percentage or dollar amount -- a quantified result "
                                + "(\"reduced latency by 40%\") is more convincing than a "
                                + "description of duties.",
                        line));
            }

            if (PASSIVE_VOICE.matcher(line).find() && passive.size() < MAX_EVIDENCE_PER_CATEGORY) {
                passive.add(new Finding(
                        SuggestionCategory.PASSIVE_VOICE, Severity.INFO,
                        "Appears to use passive voice; active voice (\"Led the migration\" "
                                + "rather than \"The migration was led by...\") reads more directly.",
                        line));
            }

            int wordCount = line.split("\\s+").length;
            if ((line.length() > TOO_LONG_CHAR_THRESHOLD || wordCount > TOO_LONG_WORD_THRESHOLD)
                    && tooLong.size() < MAX_EVIDENCE_PER_CATEGORY) {
                tooLong.add(new Finding(
                        SuggestionCategory.BULLET_TOO_LONG, Severity.INFO,
                        "This line is long (" + wordCount + " words); consider splitting "
                                + "it into two focused bullets.",
                        truncate(line)));
            }

            if (FIRST_PERSON.matcher(line).find() && firstPerson.size() < MAX_EVIDENCE_PER_CATEGORY) {
                firstPerson.add(new Finding(
                        SuggestionCategory.FIRST_PERSON_PRONOUN, Severity.WARNING,
                        "Uses a first-person pronoun; resumes conventionally omit "
                                + "\"I\"/\"my\" and start bullets directly with the verb.",
                        line));
            }
        }

        List<Finding> all = new ArrayList<>();
        all.addAll(weakVerb);
        all.addAll(unquantified);
        all.addAll(passive);
        all.addAll(tooLong);
        all.addAll(firstPerson);
        return all;
    }

    private String stripBulletMarker(String line) {
        return line.replaceFirst("^[•\\-*▪●◦‣]\\s*", "").replaceFirst("^\\d+[.)]\\s*", "");
    }

    private String truncate(String line) {
        return line.length() <= 200 ? line : line.substring(0, 197) + "...";
    }
}
