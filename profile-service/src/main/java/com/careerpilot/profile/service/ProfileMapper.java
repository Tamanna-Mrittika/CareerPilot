package com.careerpilot.profile.service;

import com.careerpilot.profile.api.dto.ProfileDtos.EducationResponse;
import com.careerpilot.profile.api.dto.ProfileDtos.ExperienceResponse;
import com.careerpilot.profile.api.dto.ProfileDtos.ProfileResponse;
import com.careerpilot.profile.api.dto.ProfileDtos.ProfileSkillResponse;
import com.careerpilot.profile.domain.Education;
import com.careerpilot.profile.domain.Experience;
import com.careerpilot.profile.domain.Profile;
import com.careerpilot.profile.domain.ProfileSkill;

import java.util.Comparator;
import java.util.List;

/** Entity to DTO translation, kept out of the controllers and services. */
final class ProfileMapper {

    private ProfileMapper() {
    }

    static ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.getId(),
                profile.getUserId(),
                profile.getFullName(),
                profile.getHeadline(),
                profile.getSummary(),
                profile.getEmail(),
                profile.getPhone(),
                profile.getLocationCity(),
                profile.getLocationCountry(),
                profile.getRemotePreference(),
                profile.getYearsExperience(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                profile.getPortfolioUrl(),
                profile.getEducation().stream()
                        .sorted(Comparator.comparing(Education::getStartDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(ProfileMapper::toResponse).toList(),
                profile.getExperience().stream()
                        .sorted(Comparator.comparing(Experience::getStartDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(ProfileMapper::toResponse).toList(),
                toSkillResponses(profile.getSkills()),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    static EducationResponse toResponse(Education education) {
        return new EducationResponse(
                education.getId(),
                education.getInstitution(),
                education.getDegree(),
                education.getFieldOfStudy(),
                education.getStartDate(),
                education.getEndDate(),
                education.getGrade(),
                education.getDescription());
    }

    static ExperienceResponse toResponse(Experience experience) {
        return new ExperienceResponse(
                experience.getId(),
                experience.getCompany(),
                experience.getTitle(),
                experience.getEmploymentType(),
                experience.getLocationCity(),
                experience.getStartDate(),
                experience.getEndDate(),
                experience.isCurrent(),
                experience.getDescription());
    }

    static List<ProfileSkillResponse> toSkillResponses(List<ProfileSkill> skills) {
        return skills.stream()
                .sorted(Comparator.comparing((ProfileSkill ps) -> ps.getSkill().getCategory())
                        .thenComparing(ps -> ps.getSkill().getName()))
                .map(ps -> new ProfileSkillResponse(
                        ps.getSkill().getId(),
                        ps.getSkill().getName(),
                        ps.getSkill().getSlug(),
                        ps.getSkill().getCategory(),
                        ps.getProficiency(),
                        ps.getYearsExperience(),
                        ps.isExtractedFromResume()))
                .toList();
    }
}
