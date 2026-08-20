package com.careerpilot.matching.repository;

import com.careerpilot.matching.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findBySkillSlugIn(Collection<String> skillSlugs);
}
