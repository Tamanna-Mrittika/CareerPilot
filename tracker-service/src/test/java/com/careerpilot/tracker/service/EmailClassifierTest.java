package com.careerpilot.tracker.service;

import com.careerpilot.tracker.domain.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classifier decides whether a candidate's application advanced or died, so its
 * failure modes are asymmetric: wrongly reporting progress on a rejection is far more
 * damaging than failing to classify at all. These tests pin that behaviour.
 */
class EmailClassifierTest {

    private final EmailClassifier classifier = new EmailClassifier();

    @Nested
    @DisplayName("rejection is detected before interview language")
    class RejectionPrecedence {

        /**
         * The single most important test in this class. Real rejection emails are usually
         * polite and reference the interview that just happened, so they contain interview
         * vocabulary verbatim. A classifier that checks interview patterns first reports
         * these as INTERVIEWING -- telling a candidate they advanced when they were turned
         * down. If someone reorders the checks in EmailClassifier, this test is what
         * catches it.
         *
         * <p>Each row is split into the half that carries interview language and the half
         * that carries the rejection, so the test can first assert the interview half really
         * does classify as INTERVIEWING <em>on its own</em>. That guard is the point: merely
         * containing the word "interview" is not enough to exercise the ordering -- an
         * earlier version of this test used a fixture that matched no interview pattern at
         * all and so passed even with the two checks swapped.
         */
        @ParameterizedTest(name = "[{index}] {0}")
        @CsvSource(delimiter = '|', value = {
                "Thank you for completing the technical interview for the Software Engineer role."
                        + " | After careful consideration we regret to inform you that we have"
                        + " decided to pursue other candidates.",
                "We enjoyed the technical screen and your take-home assignment."
                        + " | Unfortunately we have decided to pursue other candidates at this time.",
                "This note concerns the interview scheduled for Tuesday."
                        + " | Your application has been unsuccessful, so we have cancelled it."
        })
        @DisplayName("a rejection carrying real interview language still resolves to REJECTED")
        void rejectionMentioningInterviewIsNotTreatedAsProgress(String interviewHalf, String rejectionHalf) {
            assertThat(classifier.classify("Update", interviewHalf))
                    .as("fixture must contain language that genuinely triggers INTERVIEWING, "
                            + "otherwise this row does not test the ordering at all")
                    .get()
                    .extracting(EmailClassifier.Classification::status)
                    .isEqualTo(ApplicationStatus.INTERVIEWING);

            assertThat(classifier.classify("Update", interviewHalf + " " + rejectionHalf))
                    .get()
                    .extracting(EmailClassifier.Classification::status)
                    .isEqualTo(ApplicationStatus.REJECTED);
        }

        @Test
        @DisplayName("rejection mentioning a scheduled interview still resolves to REJECTED")
        void rejectionMentioningScheduledInterviewIsStillRejection() {
            Optional<EmailClassifier.Classification> result = classifier.classify(
                    "Update on your application",
                    "We enjoyed our conversation and the technical interview. "
                            + "Unfortunately we have decided not to proceed with your application.");

            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(ApplicationStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("positive outcomes")
    class Positive {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "We would like to invite you to an interview next week.",
                "Please schedule an interview using the link below.",
                "We would like to speak with you about the role.",
                "Next round of the interview process: a technical screen.",
                "Please share your availability for a call.",
                "We are sending over a take-home assignment."
        })
        @DisplayName("interview language resolves to INTERVIEWING")
        void interviewLanguage(String body) {
            assertThat(classifier.classify("Re: your application", body))
                    .get()
                    .extracting(EmailClassifier.Classification::status)
                    .isEqualTo(ApplicationStatus.INTERVIEWING);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "We are pleased to offer you the position.",
                "Please find attached your offer letter.",
                "We would like to offer you the role of Backend Engineer."
        })
        @DisplayName("offer language resolves to OFFER")
        void offerLanguage(String body) {
            assertThat(classifier.classify("Good news", body))
                    .get()
                    .extracting(EmailClassifier.Classification::status)
                    .isEqualTo(ApplicationStatus.OFFER);
        }

        /**
         * Offer must also win over interview vocabulary -- an offer email routinely thanks
         * the candidate for interviewing.
         */
        @Test
        @DisplayName("offer mentioning the interview resolves to OFFER, not INTERVIEWING")
        void offerBeatsInterviewLanguage() {
            assertThat(classifier.classify("Offer",
                    "Thank you for taking the time to interview with us. "
                            + "We are pleased to offer you the position."))
                    .get()
                    .extracting(EmailClassifier.Classification::status)
                    .isEqualTo(ApplicationStatus.OFFER);
        }
    }

    @Nested
    @DisplayName("refuses to guess")
    class NoConfidentMatch {

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "Your weekly newsletter: top 10 programming languages",
                "Your password was changed successfully",
                "Reminder: team standup at 10am",
                "Thanks for your application, we have received it"
        })
        @DisplayName("unrelated or merely-acknowledging mail classifies as nothing")
        void unrelatedMailReturnsEmpty(String body) {
            assertThat(classifier.classify("FYI", body)).isEmpty();
        }

        @Test
        @DisplayName("empty input classifies as nothing rather than throwing")
        void emptyInput() {
            assertThat(classifier.classify(null, null)).isEmpty();
            assertThat(classifier.classify("", "")).isEmpty();
            assertThat(classifier.classify("   ", "  ")).isEmpty();
        }
    }

    @Test
    @DisplayName("classification reports the phrase that triggered it")
    void reportsMatchedPhrase() {
        Optional<EmailClassifier.Classification> result = classifier.classify(
                "Interview", "We would like to invite you to an interview.");

        // The matched phrase is surfaced to the user on the audit event, so a card that
        // moves on its own can always be explained. An empty phrase would make an
        // automated transition indistinguishable from a bug.
        assertThat(result).isPresent();
        assertThat(result.get().matchedPhrase()).isNotBlank();
    }

    @Test
    @DisplayName("matching is case-insensitive")
    void caseInsensitive() {
        assertThat(classifier.classify("X", "WE REGRET TO INFORM YOU"))
                .get()
                .extracting(EmailClassifier.Classification::status)
                .isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("the subject line alone is enough to classify")
    void subjectAloneIsEnough() {
        assertThat(classifier.classify("Interview invitation for Backend Engineer", null))
                .get()
                .extracting(EmailClassifier.Classification::status)
                .isEqualTo(ApplicationStatus.INTERVIEWING);
    }
}
