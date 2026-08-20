package com.careerpilot.matching.service;

import com.careerpilot.matching.api.dto.MatchDtos.CourseResponse;
import com.careerpilot.matching.domain.Course;
import com.careerpilot.matching.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps missing skills to free learning resources.
 *
 * <p>Batched by design: a single {@code findBySkillSlugIn} for every gap in the response,
 * not one query per skill. Ranking 20 jobs with 8 gaps each would otherwise mean 160
 * queries for what is one lookup against a table of ~120 rows.
 */
@Service
@RequiredArgsConstructor
public class CourseRecommendationService {

    /** Cap per skill: three good options is a next step, twenty is a research project. */
    private static final int MAX_COURSES_PER_SKILL = 3;

    private final CourseRepository courses;

    @Transactional(readOnly = true)
    public Map<String, List<CourseResponse>> findForSkills(Collection<String> skillSlugs) {
        if (skillSlugs == null || skillSlugs.isEmpty()) {
            return Map.of();
        }

        Set<String> distinct = Set.copyOf(skillSlugs);
        Map<String, List<CourseResponse>> bySlug = new LinkedHashMap<>();

        for (Course course : courses.findBySkillSlugIn(distinct)) {
            List<CourseResponse> list = bySlug.computeIfAbsent(course.getSkillSlug(), k -> new java.util.ArrayList<>());
            if (list.size() < MAX_COURSES_PER_SKILL) {
                list.add(new CourseResponse(course.getProvider(), course.getTitle(), course.getUrl()));
            }
        }
        return bySlug;
    }
}
