package com.careerpilot.profile.api;

import com.careerpilot.profile.api.dto.SkillDtos.SkillResponse;
import com.careerpilot.profile.api.dto.SkillDtos.SkillWithAliasesResponse;
import com.careerpilot.profile.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the canonical skill taxonomy.
 *
 * <p>Two audiences: the profile wizard (autocomplete, paged) and resume-service
 * (the complete alias set, unpaged). The taxonomy is seeded by migration and has no write
 * endpoints -- letting arbitrary users add skills would fragment the vocabulary that
 * matching depends on.
 */
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Tag(name = "Skill taxonomy")
public class SkillController {

    private final SkillService skillService;

    @GetMapping
    @Operation(summary = "Search or browse skills (autocomplete for the profile wizard)")
    public Page<SkillResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return skillService.search(q, category, pageable);
    }

    @GetMapping("/categories")
    @Operation(summary = "List the distinct skill categories")
    public List<String> categories() {
        return skillService.categories();
    }

    @GetMapping("/taxonomy")
    @Operation(summary = "Full taxonomy with aliases",
            description = """
                    Consumed by resume-service to build its Aho-Corasick matching automaton.
                    Unpaged by design: a partial taxonomy would silently produce a matcher
                    that misses skills. Cache it on the client -- it changes only on migration.
                    """)
    public List<SkillWithAliasesResponse> taxonomy() {
        return skillService.allWithAliases();
    }
}
