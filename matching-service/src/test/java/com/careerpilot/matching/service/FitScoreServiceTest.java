package com.careerpilot.matching.service;

import com.careerpilot.matching.client.JobServiceClient.JobSummary;
import com.careerpilot.matching.client.ProfileServiceClient.ProfileSkill;
import com.careerpilot.matching.client.ProfileServiceClient.ProfileSnapshot;
import com.careerpilot.matching.client.ProfileServiceClient.SkillTaxonomyEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The fit score is the product this service sells, and it is meant to be explainable --
 * so the arithmetic, not just the ordering, is worth pinning. These tests use stubbed
 * rarity weights so the expected numbers can be worked out by hand rather than depending
 * on whatever the live job corpus happens to contain.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FitScoreServiceTest {

    @Mock
    private SkillRarityIndex rarityIndex;

    @Mock
    private JobSkillExtractor skillExtractor;

    private FitScoreService fitScore;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        fitScore = new FitScoreService(rarityIndex, skillExtractor);
        // Names are cosmetic here; the scoring maths never reads them.
        when(skillExtractor.resolveSlug(anyString())).thenAnswer(inv -> {
            String slug = inv.getArgument(0);
            return new SkillTaxonomyEntry(UUID.randomUUID(), slug, slug, "Backend", List.of());
        });
    }

    @Nested
    @DisplayName("rarity weighting")
    class RarityWeighting {

        /**
         * The whole point of weighting by IDF: matching one scarce requirement is worth more
         * than matching one ubiquitous one. Both candidates below match exactly one of the
         * job's two skills, so an unweighted implementation scores them identically.
         */
        @Test
        @DisplayName("matching the rare skill beats matching the common one")
        void rareSkillOutscoresCommonSkill() {
            JobSummary job = job("Backend Engineer", false, "Dhaka", "Bangladesh");
            when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of("kubernetes", "git"));
            when(rarityIndex.weight("kubernetes")).thenReturn(3.0);
            when(rarityIndex.weight("git")).thenReturn(0.5);

            double hasRare = fitScore.score(profileWith("kubernetes"), job).overallScore();
            double hasCommon = fitScore.score(profileWith("git"), job).overallScore();

            assertThat(hasRare).isGreaterThan(hasCommon);

            // And specifically: 3.0/3.5 of the job's weighted requirement vs 0.5/3.5.
            assertThat(skillComponent(fitScore.score(profileWith("kubernetes"), job)))
                    .isCloseTo(85.7, within(0.1));
            assertThat(skillComponent(fitScore.score(profileWith("git"), job)))
                    .isCloseTo(14.3, within(0.1));
        }

        @Test
        @DisplayName("gaps are reported rarest-first")
        void missingSkillsRankedByRarity() {
            JobSummary job = job("Backend Engineer", false, "Dhaka", "Bangladesh");
            when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of("kubernetes", "git", "docker"));
            when(rarityIndex.weight("kubernetes")).thenReturn(3.0);
            when(rarityIndex.weight("docker")).thenReturn(1.5);
            when(rarityIndex.weight("git")).thenReturn(0.5);

            var result = fitScore.score(profileWith(), job);

            // For a single job, the rarest gap is the one that would most improve standing.
            assertThat(result.missingSkills())
                    .extracting(FitScoreService.MissingSkill::slug)
                    .containsExactly("kubernetes", "docker", "git");
        }
    }

    @Nested
    @DisplayName("inapplicable components are excluded, not scored")
    class InapplicableComponents {

        /**
         * Location on a remote role genuinely does not apply. Scoring it 100 would inflate
         * every remote job; scoring it 0 would punish them. The only neutral option is to
         * drop it from both the numerator and the denominator.
         */
        @Test
        @DisplayName("location on a remote role is -1 and re-normalised away")
        void remoteLocationExcludedFromAverage() {
            JobSummary job = job("Backend Engineer", true, null, null);
            when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of("java", "kubernetes"));
            when(rarityIndex.weight(anyString())).thenReturn(1.0);

            var result = fitScore.score(profileWith("java"), job);

            assertThat(componentScore(result, "location")).isEqualTo(-1);

            // skills 50, experience 100, workStyle 100, location excluded:
            //   (50*0.55 + 100*0.20 + 100*0.10) / (0.55+0.20+0.10) = 57.5/0.85 = 67.6
            // Scoring location as a free 100 would give 72.5; scoring it 0 would give 57.5.
            assertThat(result.overallScore()).isCloseTo(67.6, within(0.05));
        }

        @Test
        @DisplayName("a missing profile city makes location inapplicable rather than zero")
        void unknownCandidateCityIsInapplicable() {
            JobSummary job = job("Backend Engineer", false, "Dhaka", "Bangladesh");
            when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of("java"));
            when(rarityIndex.weight(anyString())).thenReturn(1.0);

            ProfileSnapshot noCity = new ProfileSnapshot(UUID.randomUUID(), "A", null, null, null,
                    null, 5, List.of(skill("java")));

            assertThat(componentScore(fitScore.score(noCity, job), "location")).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("unassessable skills are capped, not excluded")
    class UnassessableSkillsCap {

        /**
         * The regression the cap was added for. A posting with no recognisable skills leaves
         * the dominant 55% signal *unknown*, not satisfied. Excluding it outright let such
         * jobs score a clean 100 on experience and work style alone and float to the top of
         * the ranking, above genuinely good matches.
         */
        @Test
        @DisplayName("a job with no extractable skills cannot score above 50")
        void unassessableJobIsCappedAtFifty() {
            JobSummary job = job("Backend Engineer", true, null, null);
            when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of());

            var result = fitScore.score(profileWith("java"), job);

            // Everything else is perfect, so without the cap this would be a clean 100.
            assertThat(componentScore(result, "skills")).isEqualTo(-1);
            assertThat(componentScore(result, "experience")).isEqualTo(100.0);
            assertThat(componentScore(result, "workStyle")).isEqualTo(100.0);
            assertThat(result.overallScore()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("a mediocre but verified match outranks an unassessable one")
        void verifiedMatchOutranksUnassessable() {
            JobSummary assessable = job("Backend Engineer", true, null, null);
            when(skillExtractor.extractSkillSlugs(assessable)).thenReturn(Set.of("java", "kubernetes"));
            when(rarityIndex.weight(anyString())).thenReturn(1.0);

            JobSummary contentFree = job("Backend Engineer", true, null, null);
            when(skillExtractor.extractSkillSlugs(contentFree)).thenReturn(Set.of());

            ProfileSnapshot candidate = profileWith("java");   // matches only half the skills

            // This is the ordering that was actually wrong before the cap existed.
            assertThat(fitScore.score(candidate, assessable).overallScore())
                    .isGreaterThan(fitScore.score(candidate, contentFree).overallScore());
        }

        @Test
        @DisplayName("the cap is a ceiling, not a fixed score")
        void capDoesNotRaiseALowScore() {
            JobSummary job = job("Principal Engineer", false, "Berlin", "Germany");
            when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of());

            // Junior candidate, wrong country: the other components are genuinely poor, and
            // Math.min must leave them alone rather than lifting them to 50.
            ProfileSnapshot junior = new ProfileSnapshot(UUID.randomUUID(), "A", null, "Dhaka",
                    "Bangladesh", "ONSITE", 1, List.of());

            assertThat(fitScore.score(junior, job).overallScore()).isLessThan(50.0);
        }
    }

    @Nested
    @DisplayName("experience component")
    class Experience {

        @Test
        @DisplayName("meeting the seniority bar scores full marks; exceeding it is not penalised")
        void meetingOrExceedingTheBar() {
            JobSummary senior = job("Senior Backend Engineer", true, null, null);
            when(skillExtractor.extractSkillSlugs(senior)).thenReturn(Set.of("java"));
            when(rarityIndex.weight(anyString())).thenReturn(1.0);

            assertThat(componentScore(fitScore.score(profileWithYears(5, "java"), senior), "experience"))
                    .isEqualTo(100.0);
            // Over-qualified is not a worse fit for the candidate's own purposes.
            assertThat(componentScore(fitScore.score(profileWithYears(12, "java"), senior), "experience"))
                    .isEqualTo(100.0);
        }

        @Test
        @DisplayName("falling short scales down proportionally rather than failing outright")
        void fallingShortScalesDown() {
            JobSummary senior = job("Senior Backend Engineer", true, null, null);
            when(skillExtractor.extractSkillSlugs(senior)).thenReturn(Set.of("java"));
            when(rarityIndex.weight(anyString())).thenReturn(1.0);

            // 2 years against an implied 5 -> 40%, not 0.
            assertThat(componentScore(fitScore.score(profileWithYears(2, "java"), senior), "experience"))
                    .isEqualTo(40.0);
        }
    }

    @Test
    @DisplayName("every component carries a human-readable explanation")
    void everyComponentIsExplained() {
        JobSummary job = job("Backend Engineer", false, "Dhaka", "Bangladesh");
        when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of("java"));
        when(rarityIndex.weight(anyString())).thenReturn(1.0);

        var result = fitScore.score(profileWith("java"), job);

        // An opaque percentage is not defensible to a candidate asking how it was derived,
        // which is the entire reason the breakdown exists.
        assertThat(result.breakdown())
                .hasSize(4)
                .allSatisfy(c -> assertThat(c.explanation()).isNotBlank());
        assertThat(result.breakdown())
                .extracting(FitScoreService.ComponentScore::component)
                .containsExactlyInAnyOrder("skills", "experience", "location", "workStyle");
    }

    @Test
    @DisplayName("component weights sum to 1.0")
    void weightsSumToOne() {
        JobSummary job = job("Backend Engineer", false, "Dhaka", "Bangladesh");
        when(skillExtractor.extractSkillSlugs(job)).thenReturn(Set.of("java"));
        when(rarityIndex.weight(anyString())).thenReturn(1.0);

        double total = fitScore.score(profileWith("java"), job).breakdown().stream()
                .mapToDouble(FitScoreService.ComponentScore::weight)
                .sum();

        assertThat(total).isCloseTo(1.0, within(1e-9));
    }

    // --- helpers -----------------------------------------------------------------

    private static JobSummary job(String title, boolean remote, String city, String country) {
        return new JobSummary(UUID.randomUUID(), title, "Acme",
                city == null ? null : city + ", " + country, city, country, remote,
                "FULL_TIME", "description", null, null, null, "https://example.test",
                List.of(), "REMOTIVE", "Remotive");
    }

    /** Dhaka-based candidate, 5 years, no remote preference, holding the given skills. */
    private static ProfileSnapshot profileWith(String... slugs) {
        return profileWithYears(5, slugs);
    }

    private static ProfileSnapshot profileWithYears(int years, String... slugs) {
        return new ProfileSnapshot(UUID.randomUUID(), "Candidate", "Backend Engineer",
                "Dhaka", "Bangladesh", null, years,
                java.util.Arrays.stream(slugs).map(FitScoreServiceTest::skill).toList());
    }

    private static ProfileSkill skill(String slug) {
        return new ProfileSkill(slug, slug, "Backend", "ADVANCED", 3);
    }

    private static double componentScore(FitScoreService.FitResult result, String component) {
        return result.breakdown().stream()
                .filter(c -> c.component().equals(component))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component named " + component))
                .score();
    }

    private static double skillComponent(FitScoreService.FitResult result) {
        return componentScore(result, "skills");
    }
}
