package com.careerpilot.profile.api.dto;

import com.careerpilot.profile.domain.Proficiency;
import com.careerpilot.profile.domain.RemotePreference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Profile API payloads.
 *
 * <p>Note there is no {@code userId} field on any request type. Ownership is taken from the
 * validated JWT, never from the body -- accepting it here would let any authenticated user
 * write to someone else's profile.
 */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    // ---- requests ----------------------------------------------------------

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 160) String headline,
            @Size(max = 4000) String summary,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 120) String locationCity,
            @Size(max = 120) String locationCountry,
            RemotePreference remotePreference,
            @Size(max = 500) String linkedinUrl,
            @Size(max = 500) String githubUrl,
            @Size(max = 500) String portfolioUrl) {
    }

    public record EducationRequest(
            @NotBlank @Size(max = 200) String institution,
            @Size(max = 200) String degree,
            @Size(max = 200) String fieldOfStudy,
            @PastOrPresent LocalDate startDate,
            LocalDate endDate,
            @Size(max = 50) String grade,
            @Size(max = 2000) String description) {
    }

    public record ExperienceRequest(
            @NotBlank @Size(max = 200) String company,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 50) String employmentType,
            @Size(max = 120) String locationCity,
            @NotNull @PastOrPresent LocalDate startDate,
            LocalDate endDate,
            boolean current,
            @Size(max = 4000) String description) {
    }

    public record SkillAssignment(
            @NotBlank String slug,
            Proficiency proficiency,
            Integer yearsExperience) {
    }

    /** Full replacement of the skill set -- simpler for the wizard than per-item diffing. */
    public record UpdateSkillsRequest(
            @NotNull @Valid List<SkillAssignment> skills) {
    }

    // ---- responses ---------------------------------------------------------

    public record ProfileResponse(
            UUID id,
            UUID userId,
            String fullName,
            String headline,
            String summary,
            String email,
            String phone,
            String locationCity,
            String locationCountry,
            RemotePreference remotePreference,
            Integer yearsExperience,
            String linkedinUrl,
            String githubUrl,
            String portfolioUrl,
            List<EducationResponse> education,
            List<ExperienceResponse> experience,
            List<ProfileSkillResponse> skills,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record EducationResponse(
            UUID id,
            String institution,
            String degree,
            String fieldOfStudy,
            LocalDate startDate,
            LocalDate endDate,
            String grade,
            String description) {
    }

    public record ExperienceResponse(
            UUID id,
            String company,
            String title,
            String employmentType,
            String locationCity,
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            String description) {
    }

    public record ProfileSkillResponse(
            UUID skillId,
            String name,
            String slug,
            String category,
            Proficiency proficiency,
            Integer yearsExperience,
            boolean extractedFromResume) {
    }
}
