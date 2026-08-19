package com.careerpilot.profile.repository;

import com.careerpilot.profile.domain.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExperienceRepository extends JpaRepository<Experience, UUID> {
}
