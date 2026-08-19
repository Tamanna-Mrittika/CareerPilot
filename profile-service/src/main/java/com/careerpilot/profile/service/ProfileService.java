package com.careerpilot.profile.service;

import com.careerpilot.common.error.NotFoundException;
import com.careerpilot.profile.api.dto.ProfileDtos.EducationRequest;
import com.careerpilot.profile.api.dto.ProfileDtos.ExperienceRequest;
import com.careerpilot.profile.api.dto.ProfileDtos.ProfileResponse;
import com.careerpilot.profile.api.dto.ProfileDtos.ProfileSkillResponse;
import com.careerpilot.profile.api.dto.ProfileDtos.SkillAssignment;
import com.careerpilot.profile.api.dto.ProfileDtos.UpdateProfileRequest;
import com.careerpilot.profile.domain.Education;
import com.careerpilot.profile.domain.Experience;
import com.careerpilot.profile.domain.Profile;
import com.careerpilot.profile.domain.ProfileSkill;
import com.careerpilot.profile.domain.RemotePreference;
import com.careerpilot.profile.domain.Skill;
import com.careerpilot.profile.repository.EducationRepository;
import com.careerpilot.profile.repository.ExperienceRepository;
import com.careerpilot.profile.repository.ProfileRepository;
import com.careerpilot.profile.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository profiles;
    private final SkillRepository skills;
    private final EducationRepository educationRepository;
    private final ExperienceRepository experienceRepository;

    /**
     * Returns the caller's profile, creating an empty one on first access.
     *
     * <p>Lazy creation avoids needing a cross-service hook on registration: identity-service
     * does not have to know profile-service exists, which keeps the two genuinely
     * independent. The name and email come from the caller's own verified token.
     */
    @Transactional
    public ProfileResponse getOrCreate(UUID userId, String fullName, String email) {
        Profile profile = profiles.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("Creating profile for user {}", userId);
                    return profiles.save(Profile.create(userId,
                            fullName == null ? "" : fullName, email));
                });
        return ProfileMapper.toResponse(profile);
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        return ProfileMapper.toResponse(require(userId));
    }

    @Transactional
    public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
        Profile profile = require(userId);

        profile.setFullName(request.fullName());
        profile.setHeadline(request.headline());
        profile.setSummary(request.summary());
        profile.setEmail(request.email());
        profile.setPhone(request.phone());
        profile.setLocationCity(request.locationCity());
        profile.setLocationCountry(request.locationCountry());
        profile.setRemotePreference(
                request.remotePreference() == null ? RemotePreference.ANY : request.remotePreference());
        profile.setLinkedinUrl(request.linkedinUrl());
        profile.setGithubUrl(request.githubUrl());
        profile.setPortfolioUrl(request.portfolioUrl());

        return ProfileMapper.toResponse(profiles.save(profile));
    }

    // ---- education ---------------------------------------------------------

    @Transactional
    public ProfileResponse addEducation(UUID userId, EducationRequest request) {
        Profile profile = require(userId);
        profile.addEducation(Education.create(
                request.institution(), request.degree(), request.fieldOfStudy(),
                request.startDate(), request.endDate(), request.grade(), request.description()));
        return ProfileMapper.toResponse(profiles.save(profile));
    }

    @Transactional
    public ProfileResponse deleteEducation(UUID userId, UUID educationId) {
        Profile profile = require(userId);
        // Remove via the owning collection so orphanRemoval fires, and so a caller cannot
        // delete a row belonging to someone else's profile by guessing its id.
        boolean removed = profile.getEducation().removeIf(e -> e.getId().equals(educationId));
        if (!removed) {
            throw NotFoundException.of("Education entry", educationId);
        }
        return ProfileMapper.toResponse(profiles.save(profile));
    }

    // ---- experience --------------------------------------------------------

    @Transactional
    public ProfileResponse addExperience(UUID userId, ExperienceRequest request) {
        Profile profile = require(userId);
        profile.addExperience(Experience.create(
                request.company(), request.title(), request.employmentType(),
                request.locationCity(), request.startDate(), request.endDate(),
                request.current(), request.description()));
        return ProfileMapper.toResponse(profiles.save(profile));
    }

    @Transactional
    public ProfileResponse deleteExperience(UUID userId, UUID experienceId) {
        Profile profile = require(userId);
        boolean removed = profile.getExperience().removeIf(e -> e.getId().equals(experienceId));
        if (!removed) {
            throw NotFoundException.of("Experience entry", experienceId);
        }
        profile.recalculateYearsExperience();
        return ProfileMapper.toResponse(profiles.save(profile));
    }

    // ---- skills ------------------------------------------------------------

    /**
     * Replaces the profile's skill set wholesale.
     *
     * <p>Unknown slugs are rejected rather than silently dropped: a client sending
     * "javscript" should be told it was a typo, not left believing the skill was saved.
     */
    @Transactional
    public List<ProfileSkillResponse> replaceSkills(UUID userId, List<SkillAssignment> assignments) {
        Profile profile = require(userId);

        List<String> requestedSlugs = assignments.stream().map(SkillAssignment::slug)
                .map(s -> s.toLowerCase().trim()).distinct().toList();

        Map<String, Skill> found = skills.findBySlugIn(requestedSlugs).stream()
                .collect(java.util.stream.Collectors.toMap(Skill::getSlug, Function.identity()));

        List<String> unknown = requestedSlugs.stream().filter(s -> !found.containsKey(s)).toList();
        if (!unknown.isEmpty()) {
            throw new com.careerpilot.common.error.BadRequestException(
                    "Unknown skill slugs: " + String.join(", ", unknown));
        }

        profile.getSkills().clear();
        for (SkillAssignment assignment : assignments) {
            Skill skill = found.get(assignment.slug().toLowerCase().trim());
            profile.getSkills().add(ProfileSkill.create(profile, skill,
                    assignment.proficiency(), assignment.yearsExperience(), false));
        }

        Profile saved = profiles.save(profile);
        return ProfileMapper.toSkillResponses(saved.getSkills());
    }

    @Transactional(readOnly = true)
    public List<ProfileSkillResponse> getSkills(UUID userId) {
        return ProfileMapper.toSkillResponses(require(userId).getSkills());
    }

    private Profile require(UUID userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException(
                        "No profile exists for this user yet; GET /api/v1/profiles/me creates one"));
    }
}
