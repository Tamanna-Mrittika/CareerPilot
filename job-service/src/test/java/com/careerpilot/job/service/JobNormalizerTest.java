package com.careerpilot.job.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The content hash decides which postings collapse into one. Both directions matter:
 * failing to merge shows a user the same job three times, and over-merging hides a real
 * vacancy entirely. The city component exists because an earlier version hashed only
 * title+company and merged genuinely different roles.
 */
class JobNormalizerTest {

    private final JobNormalizer normalizer = new JobNormalizer();

    @Nested
    @DisplayName("collapses the same vacancy across boards")
    class Collapses {

        @Test
        @DisplayName("identical posting on two boards hashes the same")
        void identicalPosting() {
            assertThat(normalizer.contentHash("Senior Backend Engineer", "Acme", "Dhaka", false))
                    .isEqualTo(normalizer.contentHash("Senior Backend Engineer", "Acme", "Dhaka", false));
        }

        @Test
        @DisplayName("company legal suffixes are ignored")
        void companySuffixesIgnored() {
            String base = normalizer.contentHash("Backend Engineer", "Acme", "Dhaka", false);

            // Boards rarely spell a company name identically; without suffix stripping the
            // hash would miss most genuine cross-board duplicates.
            assertThat(normalizer.contentHash("Backend Engineer", "Acme Inc.", "Dhaka", false)).isEqualTo(base);
            assertThat(normalizer.contentHash("Backend Engineer", "Acme Ltd", "Dhaka", false)).isEqualTo(base);
            assertThat(normalizer.contentHash("Backend Engineer", "Acme GmbH", "Dhaka", false)).isEqualTo(base);
        }

        @Test
        @DisplayName("case and punctuation differences are ignored")
        void caseAndPunctuationIgnored() {
            assertThat(normalizer.contentHash("Senior Backend Engineer", "Acme", "Dhaka", false))
                    .isEqualTo(normalizer.contentHash("senior backend engineer", "ACME", "dhaka", false))
                    .isEqualTo(normalizer.contentHash("Senior  Backend/Engineer!", "Acme", "Dhaka", false));
        }
    }

    @Nested
    @DisplayName("keeps genuinely different vacancies apart")
    class KeepsApart {

        /**
         * The regression this component was added for. Hashing title+company alone merged
         * the same role in different cities into one row, hiding a real posting -- a worse
         * failure than showing a duplicate.
         */
        @Test
        @DisplayName("same title and company in different cities hash differently")
        void differentCitiesDoNotMerge() {
            assertThat(normalizer.contentHash("Backend Engineer", "Acme", "Dhaka", false))
                    .isNotEqualTo(normalizer.contentHash("Backend Engineer", "Acme", "Berlin", false));
        }

        @Test
        @DisplayName("remote and on-site variants hash differently")
        void remoteFlagSeparates() {
            assertThat(normalizer.contentHash("Backend Engineer", "Acme", "Dhaka", true))
                    .isNotEqualTo(normalizer.contentHash("Backend Engineer", "Acme", "Dhaka", false));
        }

        @Test
        @DisplayName("different titles and different companies hash differently")
        void titleAndCompanySeparate() {
            String base = normalizer.contentHash("Backend Engineer", "Acme", "Dhaka", false);
            assertThat(normalizer.contentHash("Frontend Engineer", "Acme", "Dhaka", false)).isNotEqualTo(base);
            assertThat(normalizer.contentHash("Backend Engineer", "Globex", "Dhaka", false)).isNotEqualTo(base);
        }
    }

    @Test
    @DisplayName("a missing city hashes consistently rather than throwing")
    void nullCityIsStable() {
        String a = normalizer.contentHash("Backend Engineer", "Acme", null, true);
        String b = normalizer.contentHash("Backend Engineer", "Acme", null, true);

        // Remote postings frequently have no city at all.
        assertThat(a).isEqualTo(b).hasSize(64);
        assertThat(normalizer.contentHash("Backend Engineer", "Acme", "", true)).isEqualTo(a);
    }

    @Nested
    @DisplayName("HTML to plain text")
    class PlainText {

        @Test
        @DisplayName("tags are stripped and entities decoded")
        void stripsMarkup() {
            String text = normalizer.toPlainText(
                    "<p>We need <strong>Java</strong> &amp; Spring.</p><p>Apply now</p>");

            assertThat(text).contains("Java", "Spring", "Apply now");
            assertThat(text).doesNotContain("<", ">", "&amp;");
            assertThat(text).contains("&");
        }

        @Test
        @DisplayName("script and style content is removed entirely")
        void removesScriptAndStyle() {
            String text = normalizer.toPlainText(
                    "<div>Real text</div><script>var x = 'tracking';</script><style>.a{color:red}</style>");

            // Script/style bodies are not prose; leaving them in would poison both
            // full-text search and the downstream TF-IDF scoring.
            assertThat(text).contains("Real text");
            assertThat(text).doesNotContain("tracking", "color:red");
        }

        @Test
        @DisplayName("block boundaries become line breaks so paragraphs do not run together")
        void blockBoundariesBecomeNewlines() {
            String text = normalizer.toPlainText("<li>Java</li><li>Spring</li>");
            assertThat(text).doesNotContain("JavaSpring");
        }

        @Test
        @DisplayName("null and blank input return null rather than throwing")
        void nullSafe() {
            assertThat(normalizer.toPlainText(null)).isNull();
            assertThat(normalizer.toPlainText("")).isNull();
            assertThat(normalizer.toPlainText("   ")).isNull();
        }
    }
}
