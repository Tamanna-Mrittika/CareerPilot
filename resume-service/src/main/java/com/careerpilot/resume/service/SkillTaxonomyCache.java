package com.careerpilot.resume.service;

import com.careerpilot.resume.client.ProfileServiceClient;
import com.careerpilot.resume.client.ProfileServiceClient.SkillTaxonomyEntry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Trie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the built Aho-Corasick automaton for skill extraction, refreshed periodically from
 * profile-service rather than fetched per resume.
 *
 * <p>The taxonomy is ~150 entries and changes only on a Flyway migration, so a network
 * round trip on every upload would be pure waste. An {@link AtomicReference} swap keeps a
 * concurrent parse always seeing one complete, consistent snapshot rather than a trie mid-
 * rebuild.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillTaxonomyCache {

    private final ProfileServiceClient profileServiceClient;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    private record Snapshot(Trie trie, Map<String, SkillTaxonomyEntry> byMatchedKeyword) {
        static Snapshot empty() {
            return new Snapshot(Trie.builder().ignoreCase().onlyWholeWords().build(), Map.of());
        }
    }

    @PostConstruct
    void loadInitial() {
        refresh();
    }

    /**
     * Retries quickly if the initial load raced profile-service's Eureka registration on a
     * cold start of the whole stack (compose's {@code depends_on: service_healthy} waits for
     * a healthcheck, not for service discovery, so this loses the first attempt reliably).
     * Confirmed live 2026-08-21: a resume uploaded in the ~30-minute gap between a cold start
     * and the next scheduled {@link #refresh()} got zero extracted skills with no error
     * anywhere -- the exact "wrong answers, no error" failure this mirrors the fix for in
     * matching-service's {@code SkillRarityIndex}/{@code JobCorpusIdfCache}, which this cache
     * had been missing.
     */
    @Scheduled(fixedDelay = 90, initialDelay = 30, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    public void retryUntilLoaded() {
        if (isLoaded()) {
            return;
        }
        refresh();
    }

    /**
     * Every 30 minutes: frequent enough that a taxonomy change (a rare event -- it only
     * happens on a migration) shows up the same day, infrequent enough that it is nowhere
     * near profile-service's own request volume.
     */
    @Scheduled(fixedDelay = 30, initialDelay = 30, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void refresh() {
        List<SkillTaxonomyEntry> taxonomy = profileServiceClient.fetchTaxonomy();
        if (taxonomy.isEmpty()) {
            log.warn("Skill taxonomy fetch returned nothing; keeping the previous snapshot "
                    + "({} entries) rather than replacing it with an empty one",
                    snapshot.get().byMatchedKeyword().size());
            return;
        }

        // onlyWholeWords() matters: without it, "go" would match inside "ergonomic", and
        // "r" (the R language) would match inside nearly everything. ignoreCase() lower-cases
        // both the keywords and the input text before matching.
        Trie.TrieBuilder builder = Trie.builder().ignoreCase().onlyWholeWords();
        Map<String, SkillTaxonomyEntry> byKeyword = new HashMap<>();

        for (SkillTaxonomyEntry entry : taxonomy) {
            // The canonical name itself must match too, not only its aliases -- profile-service's
            // seed data lists aliases separately from the display name (e.g. "postgres"/"psql"
            // as aliases of "PostgreSQL"), so a resume that literally says "PostgreSQL" would
            // otherwise match nothing.
            addKeyword(builder, byKeyword, entry.name(), entry);
            for (String alias : entry.aliases()) {
                addKeyword(builder, byKeyword, alias, entry);
            }
        }

        snapshot.set(new Snapshot(builder.build(), byKeyword));
        log.info("Skill taxonomy refreshed: {} skills, {} matchable keywords",
                taxonomy.size(), byKeyword.size());
    }

    private void addKeyword(Trie.TrieBuilder builder, Map<String, SkillTaxonomyEntry> byKeyword,
                            String keyword, SkillTaxonomyEntry entry) {
        String normalized = keyword.toLowerCase(Locale.ENGLISH).strip();
        if (normalized.isEmpty()) {
            return;
        }
        builder.addKeyword(normalized);
        // First writer wins on a collision (e.g. two skills sharing an alias slipped past
        // profile-service's uniqueness validation) -- silently overwriting would make
        // extraction non-deterministic depending on iteration order.
        byKeyword.putIfAbsent(normalized, entry);
    }

    public Trie trie() {
        return snapshot.get().trie();
    }

    public SkillTaxonomyEntry resolve(String matchedKeyword) {
        return snapshot.get().byMatchedKeyword().get(matchedKeyword.toLowerCase(Locale.ENGLISH));
    }

    public boolean isLoaded() {
        return !snapshot.get().byMatchedKeyword().isEmpty();
    }
}
