package com.careerpilot.profile.api;

import com.careerpilot.common.security.CurrentUser;
import com.careerpilot.profile.service.CoverLetterService;
import com.careerpilot.profile.service.CoverLetterService.CoverLetterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/cover-letters")
@RequiredArgsConstructor
@Validated
@Tag(name = "Cover letters")
public class CoverLetterController {

    private final CoverLetterService coverLetterService;

    public record GenerateRequest(
            @Size(max = 200) String companyName,
            @Size(max = 200) String jobTitle,
            @Size(max = 200) String hiringManager,
            @Size(max = 8000) String customBody) {
    }

    @PostMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate a cover letter PDF from the caller's profile",
            description = "Returns application/pdf. Omit customBody to have one composed from profile data.")
    public ResponseEntity<byte[]> generate(@RequestBody GenerateRequest request) {
        byte[] pdf = coverLetterService.generate(
                CurrentUser.requireId(),
                new CoverLetterRequest(request.companyName(), request.jobTitle(),
                        request.hiringManager(), request.customBody()));

        String filename = buildFilename(request.companyName());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    /**
     * Strips everything except letters, digits and dashes. The company name reaches this
     * method as untrusted input and ends up in a Content-Disposition header, where a stray
     * quote or CRLF would be a header-injection bug.
     */
    private String buildFilename(String companyName) {
        String base = companyName == null || companyName.isBlank() ? "cover-letter"
                : "cover-letter-" + companyName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return base.replaceAll("(^-|-$)", "") + ".pdf";
    }
}
