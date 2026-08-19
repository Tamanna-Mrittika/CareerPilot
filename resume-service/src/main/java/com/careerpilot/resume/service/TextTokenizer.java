package com.careerpilot.resume.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared tokenization for keyword scoring: lowercase, strip punctuation, drop stopwords.
 *
 * <p>No stemming. "Engineering" and "Engineer" are treated as distinct tokens. Adding a
 * stemmer (Porter/Snowball) would catch a few more matches but pulls in a dependency and a
 * new source of "why did this match" surprises for what this service most needs to stay
 * simple: every scoring decision traceable to an exact token comparison, not a stemmed
 * approximation a reviewer has to trust.
 */
@Component
public class TextTokenizer {

    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9+#.]+");
    private static final Pattern TRIM_PUNCTUATION = Pattern.compile("^[.+#]+|[.+#]+$");

    /**
     * Deliberately small and generic (not resume/job-domain-specific) -- a domain stopword
     * list would risk silently dropping a genuine skill term.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "for", "to", "of",
            "in", "on", "at", "by", "with", "from", "as", "is", "are", "was", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will", "would",
            "shall", "should", "may", "might", "must", "can", "could", "this", "that",
            "these", "those", "it", "its", "we", "you", "your", "our", "their", "they",
            "he", "she", "him", "her", "his", "i", "me", "my", "us", "not", "no", "so",
            "up", "out", "about", "into", "over", "after", "before", "than", "such",
            "also", "etc", "per", "via", "using", "use", "used", "including", "include",
            "all", "any", "each", "other", "more", "most", "some", "which", "who", "whom",
            "what", "when", "where", "how", "why", "job", "role", "work", "team", "company"
    );

    /**
     * Tokenizes and drops stopwords, keeping {@code +}/{@code #} inside tokens (so
     * "c++" and "c#" survive as distinct terms rather than collapsing to "c").
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String lower = text.toLowerCase(Locale.ENGLISH);
        String[] rawTokens = NON_WORD.split(lower);

        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : rawTokens) {
            String token = TRIM_PUNCTUATION.matcher(raw).replaceAll("");
            if (token.length() < 2 || STOPWORDS.contains(token) || token.chars().noneMatch(Character::isLetter)) {
                continue;
            }
            tokens.add(token);
        }
        return List.copyOf(tokens);
    }

    /** Like {@link #tokenize}, but keeps duplicates -- needed for term-frequency counting. */
    public List<String> tokenizeWithDuplicates(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ENGLISH);
        return Arrays.stream(NON_WORD.split(lower))
                .map(raw -> TRIM_PUNCTUATION.matcher(raw).replaceAll(""))
                .filter(t -> t.length() >= 2 && !STOPWORDS.contains(t) && t.chars().anyMatch(Character::isLetter))
                .toList();
    }
}
