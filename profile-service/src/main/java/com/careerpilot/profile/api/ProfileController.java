package com.careerpilot.profile.api;

import com.careerpilot.common.security.CurrentUser;
import com.careerpilot.profile.api.dto.ProfileDtos.EducationRequest;
import com.careerpilot.profile.api.dto.ProfileDtos.ExperienceRequest;
import com.careerpilot.profile.api.dto.ProfileDtos.ProfileResponse;
import com.careerpilot.profile.api.dto.ProfileDtos.ProfileSkillResponse;
import com.careerpilot.profile.api.dto.ProfileDtos.UpdateProfileRequest;
import com.careerpilot.profile.api.dto.ProfileDtos.UpdateSkillsRequest;
import com.careerpilot.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Every route is scoped to {@code /me}. There is deliberately no
 * {@code GET /profiles/{userId}} -- with no admin use case yet, adding one would create an
 * authorisation surface that has to be defended for no current benefit.
 */
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
@Tag(name = "Profile")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    @Operation(summary = "Fetch the caller's profile, creating an empty one on first access")
    public ProfileResponse me() {
        return profileService.getOrCreate(
                CurrentUser.requireId(),
                CurrentUser.name().orElse(""),
                CurrentUser.email().orElse(null));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the caller's profile")
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(CurrentUser.requireId(), request);
    }

    // ---- education ---------------------------------------------------------

    @PostMapping("/me/education")
    @Operation(summary = "Add an education entry")
    public ProfileResponse addEducation(@Valid @RequestBody EducationRequest request) {
        return profileService.addEducation(CurrentUser.requireId(), request);
    }

    @DeleteMapping("/me/education/{educationId}")
    @Operation(summary = "Remove an education entry")
    public ProfileResponse deleteEducation(@PathVariable UUID educationId) {
        return profileService.deleteEducation(CurrentUser.requireId(), educationId);
    }

    // ---- experience --------------------------------------------------------

    @PostMapping("/me/experience")
    @Operation(summary = "Add a work experience entry",
            description = "Total years of experience is recalculated server-side, merging overlapping roles.")
    public ProfileResponse addExperience(@Valid @RequestBody ExperienceRequest request) {
        return profileService.addExperience(CurrentUser.requireId(), request);
    }

    @DeleteMapping("/me/experience/{experienceId}")
    @Operation(summary = "Remove a work experience entry")
    public ProfileResponse deleteExperience(@PathVariable UUID experienceId) {
        return profileService.deleteExperience(CurrentUser.requireId(), experienceId);
    }

    // ---- skills ------------------------------------------------------------

    @GetMapping("/me/skills")
    @Operation(summary = "List the caller's skills")
    public List<ProfileSkillResponse> skills() {
        return profileService.getSkills(CurrentUser.requireId());
    }

    @PutMapping("/me/skills")
    @Operation(summary = "Replace the caller's skill set",
            description = "Unknown skill slugs are rejected with 400 rather than silently ignored.")
    public List<ProfileSkillResponse> replaceSkills(@Valid @RequestBody UpdateSkillsRequest request) {
        return profileService.replaceSkills(CurrentUser.requireId(), request.skills());
    }
}
