package com.careerpilot.resume.api;

import com.careerpilot.common.security.CurrentUser;
import com.careerpilot.resume.api.dto.ResumeDtos.ResumeDetailResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.ResumeSummaryResponse;
import com.careerpilot.resume.api.dto.ResumeDtos.ScoreResponse;
import com.careerpilot.resume.service.AtsScoringOrchestrator;
import com.careerpilot.resume.service.ResumeUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * The async 202 flow: {@code POST} returns immediately with a status URL, the client polls
 * {@code GET /{id}} until {@code status} leaves {@code PENDING}/{@code PROCESSING}. This is
 * the isolation the original project proposal argued for -- resume parsing is CPU-heavy,
 * so it happens off the request thread on a bounded executor, never blocking the caller.
 */
@RestController
@RequestMapping("/api/v1/resumes")
@RequiredArgsConstructor
@Tag(name = "Resumes")
@Slf4j
public class ResumeController {

    private final ResumeUploadService uploadService;
    private final AtsScoringOrchestrator scoringOrchestrator;

    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "Upload a resume PDF for parsing",
            description = """
                    Returns 202 immediately with a Location header pointing at the status
                    endpoint. Parsing (text extraction, skill matching, structural ATS
                    checks, rule-based feedback) runs asynchronously; poll GET /{id} until
                    status is COMPLETED or FAILED. PDF only, 5 MB limit.
                    """)
    public ResponseEntity<ResumeSummaryResponse> upload(
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        ResumeSummaryResponse result = uploadService.upload(CurrentUser.requireId(), file);

        URI location = URI.create(publicBaseUrl(request) + "/api/v1/resumes/" + result.id());
        return ResponseEntity.accepted().location(location).body(result);
    }

    /**
     * Builds the externally-reachable base URL from {@code X-Forwarded-*} headers when
     * present, rather than trusting {@code request.getServerName()}/{@code getScheme()} --
     * those reflect resume-service's own internal Docker address (something like
     * {@code 172.18.0.x:8083}) because the gateway proxies to it on the internal network.
     * A client can never reach that address, so a {@code Location} header built from it is
     * useless. Falls back to the raw request only if no forwarded headers are present
     * (e.g. a direct call during local development, bypassing the gateway).
     */
    private String publicBaseUrl(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");

        if (StringUtils.hasText(forwardedHost)) {
            String proto = StringUtils.hasText(forwardedProto) ? forwardedProto : "http";
            return proto + "://" + forwardedHost;
        }

        log.debug("No X-Forwarded-Host on this request; falling back to the raw request URL "
                + "(expected only when resume-service is called directly, bypassing the gateway)");
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    @GetMapping("/me")
    @Operation(summary = "List the caller's resume uploads, most recent first")
    public List<ResumeSummaryResponse> mine() {
        return uploadService.listForUser(CurrentUser.requireId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Poll a resume's processing status and, once COMPLETED, its full analysis")
    public ResumeDetailResponse get(@PathVariable UUID id) {
        return uploadService.get(CurrentUser.requireId(), id);
    }

    @GetMapping("/{id}/score")
    @Operation(summary = "Score a completed resume against a specific job's description",
            description = """
                    IDF-weighted keyword coverage: which of the job description's
                    distinctive terms appear in the resume, and which are missing, ranked
                    by how much each term is worth (rare terms across the job market count
                    more than common ones). Computed on demand, not cached, so it always
                    reflects the job's current description.
                    """)
    public ScoreResponse score(@PathVariable UUID id, @RequestParam UUID jobId) {
        return scoringOrchestrator.score(CurrentUser.requireId(), id, jobId);
    }
}
