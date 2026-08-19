package com.careerpilot.job.service;

import com.careerpilot.job.provider.RawJob;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns provider-specific text into the canonical forms the rest of the system relies on.
 *
 * <p>Two jobs, both load-bearing: strip provider HTML so search and TF-IDF see prose rather
 * than markup, and compute the content hash that collapses the same vacancy listed on
 * several boards into one result.
 */
@Component
public class JobNormalizer {

    private static final Pattern SCRIPT_OR_STYLE =
            Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1>");
    private static final Pattern BLOCK_END =
            Pattern.compile("(?i)</(p|div|li|tr|h[1-6]|br)\\s*>|<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    /** Everything that is not a letter or digit, for hashing purposes. */
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    /**
     * Company suffixes stripped before hashing, so "Acme Inc." and "Acme Ltd" are recognised
     * as one employer. Without this the dedup hash misses most genuine cross-board
     * duplicates, since boards rarely record a company name identically.
     */
    private static final Pattern COMPANY_SUFFIX = Pattern.compile(
            "\\b(inc|llc|ltd|limited|gmbh|bv|nv|plc|corp|corporation|co|company|"
                    + "sa|srl|ag|ab|oy|as|pty|pte|kk|kft|spa|sarl)\\b\\.?");

    /**
     * Converts provider HTML to readable plain text.
     *
     * <p>Deliberately regex-based rather than a full HTML parser: the input is short,
     * already-sanitised descriptive markup, and the output is only ever stored as text and
     * indexed -- never re-rendered as HTML. Pulling in a parser dependency to handle
     * adversarial markup would be solving a problem this data does not have.
     */
    public String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        String text = SCRIPT_OR_STYLE.matcher(html).replaceAll(" ");
        // Convert block boundaries to newlines first, so paragraphs do not run together.
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll(" ");
        text = unescapeEntities(text);
        text = WHITESPACE.matcher(text).replaceAll(" ");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");

        return text.lines().map(String::strip).reduce((a, b) -> a + "\n" + b)
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .orElse(null);
    }

    /**
     * Content hash used for cross-provider deduplication.
     *
     * <p>Built from title + company + city + remote flag.
     *
     * <p>City earns its place here despite the messy formatting across boards. Hashing
     * without it merged genuinely different vacancies: the same role at the same employer
     * in London and in Berlin collapsed into one entry, which is a worse failure than
     * missing a duplicate. Including the normalised city errs toward showing a real posting
     * twice rather than hiding one, and cross-board duplicates that agree on the city --
     * the common case -- still collapse correctly.
     *
     * <p>The remote flag is included because a remote and an on-site role with the same
     * title at the same company are genuinely different jobs.
     */
    public String contentHash(String title, String company, String city, boolean remote) {
        String canonical = normaliseTitle(title)
                + "|" + normaliseCompany(company)
                + "|" + normaliseCity(city)
                + "|" + remote;
        return sha256(canonical);
    }

    public String contentHash(RawJob job) {
        return contentHash(job.title(), job.company(), job.locationCity(), job.remote());
    }

    /** Empty when absent, so postings with no city still hash consistently. */
    private String normaliseCity(String city) {
        if (city == null || city.isBlank()) {
            return "";
        }
        return NON_ALNUM.matcher(city.toLowerCase(Locale.ENGLISH)).replaceAll(" ").strip();
    }

    private String normaliseTitle(String title) {
        if (title == null) {
            return "";
        }
        return NON_ALNUM.matcher(title.toLowerCase(Locale.ENGLISH)).replaceAll(" ").strip();
    }

    private String normaliseCompany(String company) {
        if (company == null) {
            return "";
        }
        String lower = company.toLowerCase(Locale.ENGLISH);
        lower = COMPANY_SUFFIX.matcher(lower).replaceAll(" ");
        return NON_ALNUM.matcher(lower).replaceAll(" ").strip();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }

    /** The handful of entities that actually appear in these feeds. */
    private String unescapeEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&hellip;", "...")
                .replace("&mdash;", "-")
                .replace("&ndash;", "-");
    }
}
