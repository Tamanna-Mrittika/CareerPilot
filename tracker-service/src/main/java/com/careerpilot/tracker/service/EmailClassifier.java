package com.careerpilot.tracker.service;

import com.careerpilot.tracker.domain.ApplicationStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Infers what an inbound email means for an application's status.
 *
 * <p>Deliberately rule-based and conservative. Two design choices matter:
 *
 * <ul>
 *   <li><strong>Rejection is checked before interview.</strong> A rejection email very
 *       often contains interview vocabulary ("thank you for interviewing with us, however
 *       we have decided..."), so checking interview first would misread most rejections as
 *       progress -- the single most damaging classification error here, because it tells a
 *       candidate they advanced when they were turned down.</li>
 *   <li><strong>No match returns empty rather than guessing.</strong> An unrecognised
 *       email leaves the card exactly where the user put it. A wrong automatic move is
 *       worse than no move: the user loses trust in the board and has to audit every
 *       card.</li>
 * </ul>
 */
@Component
public class EmailClassifier {

    /**
     * Checked first, for the reason in the class docs. Phrases rather than single words:
     * "unfortunately" alone appears in plenty of neutral scheduling mail.
     */
    private static final List<Pattern> REJECTION = compile(
            "we (regret|are sorry) to inform",
            "unfortunately[, ].{0,40}(not|unable|decided|other candidates)",
            "not (moving|move) forward",
            "not (be )?(proceeding|progressing)",
            "decided (not to|to not) (proceed|move forward|continue)",
            "will not be (proceeding|moving forward|progressing)",
            "have (decided to )?(pursue|selected|chosen) (other|another)",
            "position has been filled",
            "no longer under consideration",
            "was not successful",
            "your application (has been )?(unsuccessful|rejected|declined)"
    );

    private static final List<Pattern> OFFER = compile(
            "pleased to (offer|extend)",
            "offer of employment",
            "we would like to offer",
            "job offer",
            "extend(ing)? (you )?an offer",
            "offer letter"
    );

    private static final List<Pattern> INTERVIEW = compile(
            "invit(e|ing|ation) (you )?(to|for)? ?(an? )?(interview|conversation|call|chat)",
            "schedule (an? )?(interview|call|conversation|screening)",
            "would like to (interview|speak|meet|chat|talk)",
            "interview (invitation|request|scheduled)",
            "technical (interview|screen|assessment)",
            "next (round|stage|step) (of|in) (the )?(interview|process)",
            "book (a )?(time|slot)",
            "availability for (an? )?(interview|call)",
            "coding (challenge|test|assessment)",
            "take[- ]home (assignment|test|challenge)"
    );

    public record Classification(ApplicationStatus status, String matchedPhrase) {
    }

    /**
     * @param subject email subject, may be null
     * @param body    email body, may be null
     * @return the status this email implies, or empty when nothing matched confidently
     */
    public Optional<Classification> classify(String subject, String body) {
        String text = ((subject == null ? "" : subject) + "\n" + (body == null ? "" : body))
                .toLowerCase(Locale.ENGLISH);

        if (text.isBlank()) {
            return Optional.empty();
        }

        // Order is load-bearing: see class docs.
        return firstMatch(text, REJECTION, ApplicationStatus.REJECTED)
                .or(() -> firstMatch(text, OFFER, ApplicationStatus.OFFER))
                .or(() -> firstMatch(text, INTERVIEW, ApplicationStatus.INTERVIEWING));
    }

    private Optional<Classification> firstMatch(String text, List<Pattern> patterns, ApplicationStatus status) {
        for (Pattern pattern : patterns) {
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Optional.of(new Classification(status, matcher.group()));
            }
        }
        return Optional.empty();
    }

    private static List<Pattern> compile(String... regexes) {
        return java.util.Arrays.stream(regexes)
                .map(r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE))
                .toList();
    }
}
