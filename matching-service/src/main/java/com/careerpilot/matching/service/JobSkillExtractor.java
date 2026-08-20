package com.careerpilot.matching.service;

import com.careerpilot.matching.client.JobServiceClient.JobSummary;
import com.careerpilot.matching.client.ProfileServiceClient;
import com.careerpilot.matching.client.ProfileServiceClient.SkillTaxonomyEntry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Derives the set of skills a job posting implies, by matching the shared taxonomy against
 * its title, tags and description.
 *
 * <p>Job postings do not come with structured skill lists -- they come with prose. This is
 * the same Aho-Corasick single-pass approach resume-service uses on resume text, applied
 * to the other side of the comparison, so both sides of a fit score are expressed in the
 * same vocabulary. Without this there is nothing to compare a candidate's skills *to*.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobSkillExtractor {

    private final ProfileServiceClient profileServiceClient;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    private record Snapshot(Trie trie, Map<String, SkillTaxonomyEntry> byKeyword,
                            Map<String, SkillTaxonomyEntry> bySlug) {
        static Snapshot empty() {
            return new Snapshot(Trie.builder().ignoreCase().onlyWholeWords().build(), Map.of(), Map.of());
        }
    }

    @PostConstruct
    void loadInitial() {
        refresh();
    }

    @Scheduled(fixedDelay = 30, initialDelay = 30, timeUnit = TimeUnit.MINUTES)
    public void refresh() {
        List<SkillTaxonomyEntry> taxonomy = profileServiceClient.fetchTaxonomy();
        if (taxonomy.isEmpty()) {
            log.warn("Taxonomy fetch returned nothing; keeping previous snapshot ({} keywords)",
                    snapshot.get().byKeyword().size());
            return;
        }

        // onlyWholeWords() is essential: without it "go" matches inside "ergonomic" and
        // "r" inside almost everything, which would fabricate skills for nearly every job.
        Trie.TrieBuilder builder = Trie.builder().ignoreCase().onlyWholeWords();
        Map<String, SkillTaxonomyEntry> byKeyword = new HashMap<>();
        Map<String, SkillTaxonomyEntry> bySlug = new HashMap<>();

        for (SkillTaxonomyEntry entry : taxonomy) {
            bySlug.put(entry.slug(), entry);
            addKeyword(builder, byKeyword, entry.name(), entry);
            for (String alias : entry.aliases()) {
                addKeyword(builder, byKeyword, alias, entry);
            }
        }

        snapshot.set(new Snapshot(builder.build(), byKeyword, bySlug));
        log.info("Job skill extractor refreshed: {} skills, {} keywords", taxonomy.size(), byKeyword.size());
    }

    private void addKeyword(Trie.TrieBuilder builder, Map<String, SkillTaxonomyEntry> byKeyword,
                            String keyword, SkillTaxonomyEntry entry) {
        String normalized = keyword.toLowerCase(Locale.ENGLISH).strip();
        if (normalized.isEmpty()) {
            return;
        }
        builder.addKeyword(normalized);
        byKeyword.putIfAbsent(normalized, entry);
    }

    /** The distinct skills implied by a posting's title, tags and description. */
    public Set<String> extractSkillSlugs(JobSummary job) {
        Snapshot current = snapshot.get();
        if (current.byKeyword().isEmpty()) {
            return Set.of();
        }

        StringBuilder text = new StringBuilder();
        if (job.title() != null) {
            text.append(job.title()).append('\n');
        }
        if (job.tags() != null) {
            job.tags().forEach(tag -> text.append(tag).append('\n'));
        }
        if (job.description() != null) {
            text.append(job.description());
        }

        Collection<Emit> emits = current.trie().parseText(text.toString());
        Set<String> slugs = new LinkedHashSet<>();
        for (Emit emit : emits) {
            SkillTaxonomyEntry skill = current.byKeyword().get(emit.getKeyword());
            if (skill != null) {
                slugs.add(skill.slug());
            }
        }
        return slugs;
    }

    public SkillTaxonomyEntry resolveSlug(String slug) {
        return snapshot.get().bySlug().get(slug);
    }

    public boolean isLoaded() {
        return !snapshot.get().byKeyword().isEmpty();
    }
}
