package com.careerpilot.tracker.service;

import com.careerpilot.common.error.ApiException;
import com.careerpilot.tracker.api.dto.TrackerDtos.EmailWebhookRequest;
import com.careerpilot.tracker.api.dto.TrackerDtos.WebhookResultResponse;
import com.careerpilot.tracker.domain.ApplicationStatus;
import com.careerpilot.tracker.domain.JobApplication;
import com.careerpilot.tracker.domain.TransitionSource;
import com.careerpilot.tracker.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns an inbound email into a board movement, if it can do so confidently.
 *
 * <p>Every ambiguous case resolves to "do nothing and say why". An automated system that
 * moves the wrong card is worse than one that occasionally moves nothing: the user stops
 * trusting the board and has to re-check every entry by hand, which removes the entire
 * benefit of automating it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailWebhookService {

    private final JobApplicationRepository applications;
    private final EmailClassifier classifier;
    private final ApplicationService applicationService;

    @Transactional
    public WebhookResultResponse process(EmailWebhookRequest email) {
        var classification = classifier.classify(email.subject(), email.body());
        if (classification.isEmpty()) {
            log.info("Webhook email from '{}' did not match any classification rule", email.from());
            return new WebhookResultResponse(false,
                    "No recognisable interview, offer or rejection language found; no card was moved.",
                    null, null, null, null);
        }

        Optional<JobApplication> target = findMatchingApplication(email);
        if (target.isEmpty()) {
            log.info("Webhook email classified as {} but matched no active application",
                    classification.get().status());
            return new WebhookResultResponse(false,
                    ("Email looked like a %s message, but no active application matched its company "
                            + "or job title, so no card was moved.")
                            .formatted(classification.get().status()),
                    null, null, null, classification.get().matchedPhrase());
        }

        JobApplication application = target.get();
        ApplicationStatus previous = application.getStatus();
        ApplicationStatus desired = classification.get().status();

        try {
            applicationService.applyTransition(application, desired, TransitionSource.EMAIL_WEBHOOK,
                    "Auto-detected from email: \"%s\"".formatted(classification.get().matchedPhrase()));
        } catch (ApiException e) {
            // An illegal or redundant transition is a normal outcome here, not a failure of
            // the webhook: a duplicate "thanks for interviewing" email arriving after the
            // card already moved on should be a no-op, not a 409 back to the mail provider
            // (which would retry it forever).
            log.info("Webhook classification {} not applied to {}: {}",
                    desired, application.getId(), e.getMessage());
            return new WebhookResultResponse(false,
                    "Email looked like a %s message, but the card could not move: %s"
                            .formatted(desired, e.getMessage()),
                    application.getId(), previous, previous, classification.get().matchedPhrase());
        }

        applications.save(application);
        log.info("Webhook moved application {} from {} to {}", application.getId(), previous, desired);

        return new WebhookResultResponse(true,
                "Moved from %s to %s.".formatted(previous, desired),
                application.getId(), previous, desired, classification.get().matchedPhrase());
    }

    /**
     * Finds which application an email refers to, by looking for a tracked company name
     * (and then job title) in the subject, body, or sender domain.
     *
     * <p>Company is matched before job title because it is far more distinctive: dozens of
     * tracked roles may be "Software Engineer", but only one is at a given employer. When
     * several still match, the most recently updated wins -- that is the application the
     * user is actively engaged with, so it is the likeliest subject of new mail.
     */
    private Optional<JobApplication> findMatchingApplication(EmailWebhookRequest email) {
        List<JobApplication> active = applications.findAllActive();
        if (active.isEmpty()) {
            return Optional.empty();
        }

        String haystack = ((email.subject() == null ? "" : email.subject()) + "\n"
                + (email.body() == null ? "" : email.body()) + "\n"
                + (email.from() == null ? "" : email.from()))
                .toLowerCase(Locale.ENGLISH);

        List<JobApplication> byCompany = active.stream()
                .filter(a -> containsToken(haystack, a.getCompany()))
                .sorted(Comparator.comparing(JobApplication::getUpdatedAt).reversed())
                .toList();

        if (!byCompany.isEmpty()) {
            // Narrow further by job title when the company has several tracked roles.
            if (byCompany.size() > 1) {
                Optional<JobApplication> exact = byCompany.stream()
                        .filter(a -> containsToken(haystack, a.getJobTitle()))
                        .findFirst();
                if (exact.isPresent()) {
                    return exact;
                }
            }
            return Optional.of(byCompany.get(0));
        }

        return active.stream()
                .filter(a -> containsToken(haystack, a.getJobTitle()))
                .max(Comparator.comparing(JobApplication::getUpdatedAt));
    }

    /**
     * Whole-word containment. A plain substring check would match "Acme" inside
     * "Acmetronics", and short company names would match almost anything.
     */
    private boolean containsToken(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        String normalized = needle.toLowerCase(Locale.ENGLISH).strip();
        // Strip common suffixes so "Acme Inc." in the email still matches "Acme" on the card.
        normalized = normalized.replaceAll("\\b(inc|llc|ltd|limited|gmbh|plc|corp|co)\\b\\.?", "").strip();
        if (normalized.length() < 3) {
            return false;
        }
        return Pattern.compile("\\b" + Pattern.quote(normalized) + "\\b").matcher(haystack).find();
    }
}
