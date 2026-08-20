package com.careerpilot.matching.service;

import com.careerpilot.matching.client.JobServiceClient.JobSummary;
import com.careerpilot.matching.client.ProfileServiceClient.ProfileSkill;
import com.careerpilot.matching.client.ProfileServiceClient.ProfileSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Computes an explainable fit score between a candidate profile and a job.
 *
 * <p>Four weighted components. Every one contributes a number the caller can see and a
 * sentence explaining it -- an opaque "78% match" is not useful to a candidate and is not
 * defensible to anyone asking how it was derived.
 *
 * <p>Weights reflect what actually determines whether an application is viable: skills
 * dominate, because that is what a hiring filter screens on first; location is a hard
 * practical constraint for on-site roles but irrelevant for remote ones (so it is
 * re-normalised away rather than scored as a free win); experience and seniority refine
 * the ranking rather than driving it.
 */
@Service
@RequiredArgsConstructor
public class FitScoreService {

    private static final double WEIGHT_SKILLS = 0.55;
    private static final double WEIGHT_EXPERIENCE = 0.20;
    private static final double WEIGHT_LOCATION = 0.15;
    private static final double WEIGHT_WORK_STYLE = 0.10;

    /** Ceiling applied when the skills component could not be assessed -- see score(). */
    private static final double UNASSESSABLE_SKILLS_SCORE_CAP = 50.0;

    private final SkillRarityIndex rarityIndex;
    private final JobSkillExtractor skillExtractor;

    public record ComponentScore(String component, double score, double weight, String explanation) {
    }

    public record MatchedSkill(String slug, String name, String category, double rarityWeight, String proficiency) {
    }

    public record MissingSkill(String slug, String name, String category, double rarityWeight) {
    }

    public record FitResult(
            double overallScore,
            List<ComponentScore> breakdown,
            List<MatchedSkill> matchedSkills,
            List<MissingSkill> missingSkills) {
    }

    public FitResult score(ProfileSnapshot profile, JobSummary job) {
        Set<String> jobSkillSlugs = skillExtractor.extractSkillSlugs(job);

        Map<String, ProfileSkill> profileSkillsBySlug = new LinkedHashMap<>();
        if (profile.skills() != null) {
            profile.skills().forEach(s -> profileSkillsBySlug.put(s.slug(), s));
        }

        SkillComponent skills = scoreSkills(jobSkillSlugs, profileSkillsBySlug);
        ComponentScore experience = scoreExperience(profile, job);
        ComponentScore location = scoreLocation(profile, job);
        ComponentScore workStyle = scoreWorkStyle(profile, job);

        List<ComponentScore> breakdown = List.of(skills.component(), experience, location, workStyle);

        // Re-normalise over the components that actually applied. A remote job has no
        // meaningful location score; giving it a free 100% would inflate remote jobs
        // against local ones, and giving it 0% would punish them. Excluding it from both
        // numerator and denominator is the only neutral option.
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        for (ComponentScore component : breakdown) {
            if (component.score() >= 0) {
                weightedSum += component.score() * component.weight();
                totalWeight += component.weight();
            }
        }
        double overall = totalWeight == 0.0 ? 0.0 : weightedSum / totalWeight;

        // Cap the score when skills could not be assessed at all.
        //
        // Excluding an inapplicable component is correct for location on a remote role --
        // location genuinely does not constrain it. It is NOT correct for skills: "this
        // posting had no recognisable skills" means the dominant signal (55% of the score)
        // is unknown, not satisfied. Without this cap such jobs scored a clean 100% on
        // experience + work style alone and floated to the TOP of the ranking, above
        // genuinely good matches -- observed with content-free listings whose descriptions
        // carry no technical terms. A match we cannot verify must not outrank one we can.
        if (skills.component().score() < 0) {
            overall = Math.min(overall, UNASSESSABLE_SKILLS_SCORE_CAP);
        }

        return new FitResult(round(overall), breakdown, skills.matched(), skills.missing());
    }

    private record SkillComponent(ComponentScore component, List<MatchedSkill> matched, List<MissingSkill> missing) {
    }

    /**
     * Rarity-weighted skill coverage: of the total "value" of what this job asks for, how
     * much does the candidate have? A candidate matching one rare requirement can outscore
     * one matching three ubiquitous ones, which is the intended behaviour.
     */
    private SkillComponent scoreSkills(Set<String> jobSkillSlugs, Map<String, ProfileSkill> profileSkills) {
        if (jobSkillSlugs.isEmpty()) {
            // Nothing extractable from the posting (very short description, or the taxonomy
            // covers none of it). Scoring 0 would be wrong -- it is missing data, not a bad
            // match -- so mark the component inapplicable and let the others carry the score.
            return new SkillComponent(
                    new ComponentScore("skills", -1, WEIGHT_SKILLS,
                            "This posting did not list recognisable skills, so skill match could not be assessed."),
                    List.of(), List.of());
        }

        List<MatchedSkill> matched = new java.util.ArrayList<>();
        List<MissingSkill> missing = new java.util.ArrayList<>();
        double matchedWeight = 0.0;
        double totalWeight = 0.0;

        for (String slug : jobSkillSlugs) {
            double weight = rarityIndex.weight(slug);
            totalWeight += weight;

            var taxonomyEntry = skillExtractor.resolveSlug(slug);
            String name = taxonomyEntry != null ? taxonomyEntry.name() : slug;
            String category = taxonomyEntry != null ? taxonomyEntry.category() : "Unknown";

            ProfileSkill held = profileSkills.get(slug);
            if (held != null) {
                matchedWeight += weight;
                matched.add(new MatchedSkill(slug, name, category, round(weight), held.proficiency()));
            } else {
                missing.add(new MissingSkill(slug, name, category, round(weight)));
            }
        }

        matched.sort(Comparator.comparingDouble(MatchedSkill::rarityWeight).reversed());
        // Rarest gaps first: the missing skill that would most improve this candidate's
        // standing, not just the first one alphabetically.
        missing.sort(Comparator.comparingDouble(MissingSkill::rarityWeight).reversed());

        double score = totalWeight == 0.0 ? 0.0 : (matchedWeight / totalWeight) * 100.0;
        String explanation = "Matched %d of %d skills this role asks for, weighted by how rare each is across current postings."
                .formatted(matched.size(), jobSkillSlugs.size());

        return new SkillComponent(
                new ComponentScore("skills", round(score), WEIGHT_SKILLS, explanation), matched, missing);
    }

    /**
     * Experience fit against the seniority the posting implies. Meeting the bar scores
     * full marks; exceeding it is not penalised (being over-qualified is not a worse fit
     * for the candidate's purposes here), and falling short scales down proportionally.
     */
    private ComponentScore scoreExperience(ProfileSnapshot profile, JobSummary job) {
        Integer years = profile.yearsExperience();
        if (years == null) {
            return new ComponentScore("experience", -1, WEIGHT_EXPERIENCE,
                    "No work experience recorded on your profile, so experience fit could not be assessed.");
        }

        int required = impliedYearsRequired(job);
        if (required == 0) {
            return new ComponentScore("experience", 100.0, WEIGHT_EXPERIENCE,
                    "This posting implies no specific experience level; your %d year(s) are not a barrier."
                            .formatted(years));
        }

        double score = years >= required ? 100.0 : ((double) years / required) * 100.0;
        String explanation = years >= required
                ? "You have %d year(s); this role reads as roughly %d+ year(s), so you meet the bar."
                        .formatted(years, required)
                : "You have %d year(s); this role reads as roughly %d+ year(s), so you fall a little short."
                        .formatted(years, required);

        return new ComponentScore("experience", round(score), WEIGHT_EXPERIENCE, explanation);
    }

    /** Seniority keywords in the title are the most reliable signal a posting gives. */
    private int impliedYearsRequired(JobSummary job) {
        String title = job.title() == null ? "" : job.title().toLowerCase(Locale.ENGLISH);
        if (title.contains("intern") || title.contains("trainee")) {
            return 0;
        }
        if (title.contains("junior") || title.contains("entry") || title.contains("graduate")) {
            return 1;
        }
        if (title.contains("principal") || title.contains("staff") || title.contains("head of")
                || title.contains("director")) {
            return 8;
        }
        if (title.contains("senior") || title.contains("sr.") || title.contains("lead")) {
            return 5;
        }
        return 2;   // an untitled/mid role
    }

    /**
     * Location fit. Returns -1 (inapplicable) for remote roles rather than a score --
     * location genuinely does not constrain a remote job, and scoring it either way would
     * distort the comparison between remote and local listings.
     */
    private ComponentScore scoreLocation(ProfileSnapshot profile, JobSummary job) {
        if (job.remote()) {
            return new ComponentScore("location", -1, WEIGHT_LOCATION,
                    "This role is remote, so location does not apply.");
        }

        String candidateCity = safeLower(profile.locationCity());
        String jobCity = safeLower(job.city());
        String jobLocation = safeLower(job.location());

        if (candidateCity.isEmpty()) {
            return new ComponentScore("location", -1, WEIGHT_LOCATION,
                    "No city on your profile, so location fit could not be assessed.");
        }
        if (jobCity.isEmpty() && jobLocation.isEmpty()) {
            return new ComponentScore("location", -1, WEIGHT_LOCATION,
                    "This posting does not state a location, so location fit could not be assessed.");
        }

        // Substring both ways: boards write "Dhaka", "Dhaka, Bangladesh" and "Dhaka
        // Division" for the same place, so exact equality would miss most real matches.
        boolean sameCity = (!jobCity.isEmpty() && (jobCity.contains(candidateCity) || candidateCity.contains(jobCity)))
                || (!jobLocation.isEmpty() && jobLocation.contains(candidateCity));

        if (sameCity) {
            return new ComponentScore("location", 100.0, WEIGHT_LOCATION,
                    "This role is in %s, where you are based.".formatted(
                            job.city() != null ? job.city() : job.location()));
        }

        String candidateCountry = safeLower(profile.locationCountry());
        String jobCountry = safeLower(job.country());
        if (!candidateCountry.isEmpty() && !jobCountry.isEmpty()
                && (jobCountry.contains(candidateCountry) || candidateCountry.contains(jobCountry))) {
            return new ComponentScore("location", 50.0, WEIGHT_LOCATION,
                    "Same country but a different city -- relocation or commuting may be needed.");
        }

        return new ComponentScore("location", 0.0, WEIGHT_LOCATION,
                "This role is in %s, away from your recorded location."
                        .formatted(job.location() != null ? job.location() : job.city()));
    }

    /** Does the candidate's stated remote preference match how this role is offered? */
    private ComponentScore scoreWorkStyle(ProfileSnapshot profile, JobSummary job) {
        String preference = profile.remotePreference() == null
                ? "ANY" : profile.remotePreference().toUpperCase(Locale.ENGLISH);

        if ("ANY".equals(preference)) {
            return new ComponentScore("workStyle", 100.0, WEIGHT_WORK_STYLE,
                    "You have no remote/on-site preference set, so this role is not excluded.");
        }

        boolean wantsRemote = "REMOTE".equals(preference);
        boolean wantsOnsite = "ONSITE".equals(preference);

        if (job.remote() && wantsRemote) {
            return new ComponentScore("workStyle", 100.0, WEIGHT_WORK_STYLE,
                    "This role is remote, which matches your preference.");
        }
        if (!job.remote() && wantsOnsite) {
            return new ComponentScore("workStyle", 100.0, WEIGHT_WORK_STYLE,
                    "This role is on-site, which matches your preference.");
        }
        if ("HYBRID".equals(preference)) {
            return new ComponentScore("workStyle", 70.0, WEIGHT_WORK_STYLE,
                    "You prefer hybrid; this role is listed as %s.".formatted(job.remote() ? "remote" : "on-site"));
        }
        return new ComponentScore("workStyle", 25.0, WEIGHT_WORK_STYLE,
                "You prefer %s work; this role is %s.".formatted(
                        preference.toLowerCase(Locale.ENGLISH), job.remote() ? "remote" : "on-site"));
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ENGLISH).strip();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
