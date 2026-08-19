package com.careerpilot.profile.repository;

import com.careerpilot.profile.domain.ProfileSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileSkillRepository extends JpaRepository<ProfileSkill, UUID> {

    List<ProfileSkill> findByProfileId(UUID profileId);

    void deleteByProfileIdAndSkillId(UUID profileId, UUID skillId);
}
