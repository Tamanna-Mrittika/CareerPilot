package com.careerpilot.resume.service;

import com.careerpilot.resume.client.ProfileServiceClient.SkillTaxonomyEntry;
import lombok.RequiredArgsConstructor;
import org.ahocorasick.trie.Emit;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts skills from resume text in a single pass over the whole document, regardless of
 * how large the taxonomy grows -- that is the entire point of Aho-Corasick over the naive
 * alternative of running ~150 separate substring searches.
 */
@Service
@RequiredArgsConstructor
public class SkillExtractionService {

    private final SkillTaxonomyCache taxonomyCache;

    public record SkillMatch(SkillTaxonomyEntry skill, int occurrenceCount) {
    }

    public List<SkillMatch> extract(String text) {
        if (text == null || text.isBlank() || !taxonomyCache.isLoaded()) {
            return List.of();
        }

        Collection<Emit> emits = taxonomyCache.trie().parseText(text);

        // Aliases of the SAME skill can both appear ("Postgres" and "PostgreSQL" in one
        // resume) and must collapse to one entry with a combined count, not two rows.
        Map<String, SkillMatch> bySkillSlug = new LinkedHashMap<>();
        for (Emit emit : emits) {
            SkillTaxonomyEntry skill = taxonomyCache.resolve(emit.getKeyword());
            if (skill == null) {
                continue;
            }
            bySkillSlug.merge(skill.slug(),
                    new SkillMatch(skill, 1),
                    (existing, added) -> new SkillMatch(existing.skill(), existing.occurrenceCount() + 1));
        }

        return List.copyOf(bySkillSlug.values());
    }
}
