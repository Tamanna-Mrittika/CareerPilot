package com.careerpilot.resume.service;

import com.careerpilot.resume.client.ProfileServiceClient.SkillTaxonomyEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * IDF-weighted keyword coverage: how much of a target job description's distinctive
 * vocabulary appears in the resume.
 *
 * <p>This is what real ATS keyword-match tools compute in practice (Jobscan and similar),
 * and it is a legitimate, explainable application of TF-IDF even though it is not
 * "classic" multi-document TF-IDF ranking. The job description's own term frequency
 * doesn't matter much for a single short document; what matters is <em>which</em> of its
 * terms are rare across the job market (per {@link JobCorpusIdfCache}) and therefore
 * carry real signal if matched or missed. Every number in the output traces back to one
 * rule: this term is worth this much, and it is or is not present in the resume.
 */
@Service
@RequiredArgsConstructor
public class AtsScoringService {

    private final TextTokenizer tokenizer;
    private final JobCorpusIdfCache idfCache;
    private final SkillTaxonomyCache taxonomyCache;

    public record TermWeight(String term, double weight) {
    }

    public record ScoreResult(
            double overallScore,
            List<TermWeight> matchedTerms,
            List<TermWeight> missingTerms,
            /**
             * The subset of missing terms that are recognised skills. This is the
             * actionable list -- see {@link #actionableGaps}.
             */
            List<TermWeight> actionableGaps) {
    }

    public ScoreResult score(String resumeText, String jobDescription) {
        Set<String> resumeTokens = new HashSet<>(tokenizer.tokenize(resumeText));
        List<String> jobTokens = tokenizer.tokenize(jobDescription);

        if (jobTokens.isEmpty()) {
            return new ScoreResult(0.0, List.of(), List.of(), List.of());
        }

        List<TermWeight> matched = jobTokens.stream()
                .filter(resumeTokens::contains)
                .map(term -> new TermWeight(term, idfCache.idf(term)))
                .sorted(Comparator.comparingDouble(TermWeight::weight).reversed())
                .toList();

        List<TermWeight> missing = jobTokens.stream()
                .filter(term -> !resumeTokens.contains(term))
                .map(term -> new TermWeight(term, idfCache.idf(term)))
                // Highest-impact gaps first -- the missing keyword most worth adding.
                .sorted(Comparator.comparingDouble(TermWeight::weight).reversed())
                .toList();

        double matchedWeight = matched.stream().mapToDouble(TermWeight::weight).sum();
        double totalWeight = matchedWeight + missing.stream().mapToDouble(TermWeight::weight).sum();
        double overallScore = totalWeight == 0.0 ? 0.0 : (matchedWeight / totalWeight) * 100.0;

        return new ScoreResult(round(overallScore), matched, missing, actionableGaps(missing));
    }

    /**
     * Filters the missing-term list down to terms that are actually recognised skills.
     *
     * <p>Raw IDF ranking is useless as advice on its own, and the first real test showed
     * exactly why: scoring a backend resume against an iOS posting ranked
     * {@code bitmorpher}, {@code dhaka}, {@code negotiable}, {@code bitmorpher.com} and
     * {@code iosjobs} as the "highest-impact gaps". Those score highest *because* IDF
     * rewards rarity, and a company name, a city, a salary note or a hashtag is maximally
     * rare -- while being something a candidate can do precisely nothing about.
     *
     * <p>Intersecting against the skill taxonomy keeps the statistical weighting (rare
     * real skills still outrank common ones) but restricts the recommendation to things a
     * person could genuinely learn or add. The unfiltered {@code missingTerms} list stays
     * available for transparency and debugging.
     */
    private List<TermWeight> actionableGaps(List<TermWeight> missing) {
        if (!taxonomyCache.isLoaded()) {
            return List.of();
        }
        return missing.stream()
                .filter(t -> {
                    SkillTaxonomyEntry skill = taxonomyCache.resolve(t.term().toLowerCase(Locale.ENGLISH));
                    return skill != null;
                })
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
