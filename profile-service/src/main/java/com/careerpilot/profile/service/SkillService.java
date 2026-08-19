package com.careerpilot.profile.service;

import com.careerpilot.profile.api.dto.SkillDtos.SkillResponse;
import com.careerpilot.profile.api.dto.SkillDtos.SkillWithAliasesResponse;
import com.careerpilot.profile.domain.Skill;
import com.careerpilot.profile.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skills;

    @Transactional(readOnly = true)
    public Page<SkillResponse> search(String query, String category, Pageable pageable) {
        Page<Skill> page;
        if (StringUtils.hasText(query)) {
            page = skills.search(query.trim(), pageable);
        } else if (StringUtils.hasText(category)) {
            page = skills.findByCategoryIgnoreCase(category.trim(), pageable);
        } else {
            page = skills.findAll(pageable);
        }
        return page.map(s -> new SkillResponse(s.getId(), s.getName(), s.getSlug(), s.getCategory()));
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return skills.findDistinctCategories();
    }

    /**
     * The full taxonomy with aliases, consumed by resume-service to build its
     * Aho-Corasick automaton.
     *
     * <p>Unpaged on purpose: the caller needs the complete set to build a correct
     * automaton, and a partial page would silently produce a matcher that misses skills.
     * The taxonomy is a few hundred rows and changes rarely, so the caller caches it.
     */
    @Transactional(readOnly = true)
    public List<SkillWithAliasesResponse> allWithAliases() {
        return skills.findAllWithAliases().stream()
                .sorted(Comparator.comparing(Skill::getName))
                .map(s -> new SkillWithAliasesResponse(
                        s.getId(), s.getName(), s.getSlug(), s.getCategory(),
                        s.getAliases().stream().sorted().toList()))
                .toList();
    }
}
