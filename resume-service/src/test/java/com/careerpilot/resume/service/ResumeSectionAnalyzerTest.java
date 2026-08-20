package com.careerpilot.resume.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Years-of-experience inference is the number a candidate is most likely to dispute, and
 * both of its non-obvious rules -- scope the scan to the experience section, and merge
 * overlapping roles rather than summing them -- were added in response to real wrong
 * answers rather than in anticipation.
 */
class ResumeSectionAnalyzerTest {

    private final ResumeSectionAnalyzer analyzer = new ResumeSectionAnalyzer();

    @Nested
    @DisplayName("overlapping roles are merged, not summed")
    class OverlapMerge {

        /**
         * Someone who freelanced while employed full time has not lived through two
         * simultaneous careers. Summing durations reports four years for three years of
         * calendar time.
         */
        @Test
        @DisplayName("a contract nested inside a full-time role adds nothing")
        void fullyOverlappingRolesCountOnce() {
            String withoutContract = """
                    EXPERIENCE
                    Senior Engineer, Acme
                    Jan 2020 - Dec 2022
                    """;
            String withContract = """
                    EXPERIENCE
                    Senior Engineer, Acme
                    Jan 2020 - Dec 2022
                    Freelance Consultant, Self-employed
                    Jan 2021 - Dec 2021
                    """;

            assertThat(analyzer.inferYearsExperience(withoutContract)).isEqualTo(3);
            assertThat(analyzer.inferYearsExperience(withContract))
                    .as("a role entirely inside another adds no calendar time")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("partially overlapping roles count the union, not the sum")
        void partialOverlapCountsTheUnion() {
            String text = """
                    EXPERIENCE
                    Engineer, Acme
                    Jan 2020 - Dec 2022
                    Advisor, Globex
                    Jan 2022 - Dec 2023
                    """;

            // Union is Jan 2020 - Dec 2023 (4 years). Summing would report 3 + 2 = 5.
            assertThat(analyzer.inferYearsExperience(text)).isEqualTo(4);
        }

        @Test
        @DisplayName("genuinely separate roles do add up")
        void disjointRolesAreSummed() {
            String text = """
                    EXPERIENCE
                    Engineer, Acme
                    Jan 2016 - Dec 2017
                    Engineer, Globex
                    Jan 2020 - Dec 2021
                    """;

            // Two years plus two years, with the gap between them correctly excluded.
            assertThat(analyzer.inferYearsExperience(text)).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("only the experience section is scanned")
    class SectionScoping {

        /**
         * The bug this rule fixed: a resume describing about seven years reported twelve,
         * because a degree's "2015 - 2019" was being counted as employment.
         */
        @Test
        @DisplayName("education date ranges are not counted as work experience")
        void educationDatesAreIgnored() {
            String text = """
                    EXPERIENCE
                    Software Engineer, Acme
                    Jan 2020 - Dec 2022

                    EDUCATION
                    BSc Computer Science, IUT
                    Jan 2012 - Dec 2016
                    """;

            // Three years of work. Scanning the whole document would report eight.
            assertThat(analyzer.inferYearsExperience(text)).isEqualTo(3);
        }

        @Test
        @DisplayName("a project section after experience does not leak in")
        void projectDatesAreIgnored() {
            String text = """
                    EXPERIENCE
                    Software Engineer, Acme
                    Jan 2020 - Dec 2022

                    PROJECTS
                    Open-source contributor
                    Jan 2014 - Dec 2018
                    """;

            assertThat(analyzer.inferYearsExperience(text)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("open-ended and unusable input")
    class EdgeCases {

        @Test
        @DisplayName("'Present' is resolved against today")
        void presentMeansNow() {
            int startYear = LocalDate.now().getYear() - 4;
            String text = "EXPERIENCE\nEngineer, Acme\nJan " + startYear + " - Present\n";

            // Bounded rather than exact: the answer legitimately grows with wall-clock time,
            // so pinning a constant would make this test fail on a calendar boundary.
            assertThat(analyzer.inferYearsExperience(text)).isBetween(4, 5);
        }

        @Test
        @DisplayName("a resume with no date ranges returns null rather than zero")
        void noDatesReturnsNull() {
            String text = """
                    EXPERIENCE
                    Software Engineer, Acme
                    Built things and shipped them.
                    """;

            // null means "unknown", which downstream scoring treats as inapplicable.
            // Returning 0 would assert the candidate has no experience, which is a claim
            // the document does not support.
            assertThat(analyzer.inferYearsExperience(text)).isNull();
        }

        @Test
        @DisplayName("null and blank text return null rather than throwing")
        void nullSafe() {
            assertThat(analyzer.inferYearsExperience(null)).isNull();
            assertThat(analyzer.inferYearsExperience("")).isNull();
            assertThat(analyzer.inferYearsExperience("   ")).isNull();
        }

        @Test
        @DisplayName("a reversed range is discarded rather than counted negatively")
        void reversedRangeIgnored() {
            String text = """
                    EXPERIENCE
                    Engineer, Acme
                    Jan 2022 - Dec 2019
                    """;

            assertThat(analyzer.inferYearsExperience(text)).isNull();
        }
    }

    @Nested
    @DisplayName("missing section detection")
    class MissingSections {

        @Test
        @DisplayName("a complete resume reports nothing missing")
        void completeResume() {
            String text = """
                    EXPERIENCE
                    Software Engineer, Acme

                    EDUCATION
                    BSc Computer Science

                    SKILLS
                    Java, Spring, Kubernetes
                    """;

            assertThat(analyzer.findMissingSections(text)).isEmpty();
        }

        @Test
        @DisplayName("an absent section is named in the feedback")
        void reportsTheMissingSectionByName() {
            String text = """
                    EXPERIENCE
                    Software Engineer, Acme

                    EDUCATION
                    BSc Computer Science
                    """;

            assertThat(analyzer.findMissingSections(text))
                    .extracting(ResumeSectionAnalyzer.MissingSection::sectionName)
                    .containsExactly("Skills");
        }

        @Test
        @DisplayName("'Work History' is accepted as an Experience header")
        void acceptsHeaderSynonyms() {
            String text = """
                    WORK HISTORY
                    Software Engineer, Acme

                    EDUCATION
                    BSc Computer Science

                    SKILLS
                    Java
                    """;

            // Real resumes label this section several different ways; flagging a present
            // section as missing would be a false accusation in the feedback report.
            assertThat(analyzer.findMissingSections(text)).isEmpty();
        }

        @Test
        @DisplayName("a header must be on its own line, not buried in a sentence")
        void headerMustBeALine() {
            String text = "I have experience with education and skills across many teams.";

            assertThat(analyzer.findMissingSections(text))
                    .extracting(ResumeSectionAnalyzer.MissingSection::sectionName)
                    .containsExactlyInAnyOrder("Experience", "Education", "Skills");
        }

        @Test
        @DisplayName("null and blank text return an empty list rather than throwing")
        void nullSafe() {
            assertThat(analyzer.findMissingSections(null)).isEmpty();
            assertThat(analyzer.findMissingSections("  ")).isEmpty();
        }
    }
}
