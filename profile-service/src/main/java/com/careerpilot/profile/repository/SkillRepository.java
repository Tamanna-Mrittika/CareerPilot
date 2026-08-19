package com.careerpilot.profile.repository;

import com.careerpilot.profile.domain.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    Optional<Skill> findBySlug(String slug);

    List<Skill> findBySlugIn(Collection<String> slugs);

    /** Autocomplete for the profile wizard: matches the display name or any alias. */
    @Query("""
            select distinct s from Skill s
            left join s.aliases a
            where lower(s.name) like lower(concat('%', :q, '%'))
               or lower(a) like lower(concat('%', :q, '%'))
            """)
    Page<Skill> search(@Param("q") String query, Pageable pageable);

    Page<Skill> findByCategoryIgnoreCase(String category, Pageable pageable);

    /**
     * The whole taxonomy with aliases eagerly attached, for resume-service to build its
     * matching automaton. Fetching aliases here rather than lazily is deliberate: the
     * caller needs every one of them, so lazy loading would mean one query per skill.
     */
    @EntityGraph(attributePaths = "aliases")
    @Query("select s from Skill s")
    List<Skill> findAllWithAliases();

    @Query("select distinct s.category from Skill s order by s.category")
    List<String> findDistinctCategories();
}
