package com.careerpilot.profile.repository;

import com.careerpilot.profile.domain.Education;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EducationRepository extends JpaRepository<Education, UUID> {
}
