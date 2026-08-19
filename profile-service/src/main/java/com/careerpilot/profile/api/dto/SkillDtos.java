package com.careerpilot.profile.api.dto;

import java.util.List;
import java.util.UUID;

public final class SkillDtos {

    private SkillDtos() {
    }

    public record SkillResponse(
            UUID id,
            String name,
            String slug,
            String category) {
    }

    /**
     * Skill plus every alias, served to resume-service so it can build its extraction
     * automaton. Separate from {@link SkillResponse} because the alias list is large and
     * pointless for UI autocomplete -- the two consumers genuinely want different shapes.
     */
    public record SkillWithAliasesResponse(
            UUID id,
            String name,
            String slug,
            String category,
            List<String> aliases) {
    }
}
