package com.careerpilot.tracker.api;

import com.careerpilot.common.error.ApiException;
import com.careerpilot.tracker.api.dto.TrackerDtos.EmailWebhookRequest;
import com.careerpilot.tracker.api.dto.TrackerDtos.WebhookResultResponse;
import com.careerpilot.tracker.security.WebhookSignatureVerifier;
import com.careerpilot.tracker.service.EmailWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Inbound email webhooks.
 *
 * <p>Two entry points, deliberately, because they have different callers and therefore
 * different auth:
 *
 * <ul>
 *   <li>{@code POST /email} -- machine-to-machine, called by a mail provider that has no
 *       user session. Unauthenticated in the JWT sense, secured instead by an HMAC
 *       signature over the raw body.</li>
 *   <li>{@code POST /email/simulate} -- for demos and manual testing, secured by the normal
 *       JWT and restricted to ADMIN. This exists so the demo does not depend on real mail
 *       arriving, <em>without</em> weakening the real endpoint: the signature check on
 *       {@code /email} is never bypassed or made optional.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Email webhooks")
public class WebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final EmailWebhookService webhookService;
    private final ObjectMapper objectMapper;

    /**
     * Takes the body as {@code byte[]}, not a parsed object, because the signature is
     * computed over the exact bytes sent. Letting Spring bind the JSON first and
     * re-serialising it would change whitespace and key order, and no valid signature
     * would ever verify.
     */
    @PostMapping(value = "/email", consumes = "application/json")
    @Operation(summary = "Inbound email from a mail provider (HMAC-signed)",
            description = """
                    Requires an `X-Signature-256: sha256=<hex>` header containing an
                    HMAC-SHA256 of the raw request body under the shared secret. Verification
                    is constant-time and fails closed when no secret is configured.

                    Always returns 200 with a description of what happened -- including when
                    nothing matched. Returning an error for "no card moved" would make a mail
                    provider retry the same message indefinitely.
                    """)
    public WebhookResultResponse receiveEmail(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Signature-256", required = false) String signature) {

        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("Rejected webhook with missing or invalid signature");
            throw new InvalidSignatureException();
        }

        EmailWebhookRequest email = parse(rawBody);
        return webhookService.process(email);
    }

    @PostMapping("/email/simulate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Simulate an inbound email (ADMIN only)",
            description = """
                    Same processing as the real webhook, authenticated by JWT instead of an
                    HMAC signature, so a demo can trigger an auto-transition without sending
                    real mail. ADMIN-only because it can move any user's card.
                    """)
    public WebhookResultResponse simulate(@RequestBody EmailWebhookRequest email) {
        return webhookService.process(email);
    }

    /**
     * Returns the signature a given payload would need, so the real signed endpoint can be
     * exercised end-to-end during a demo. ADMIN-only: handing out valid signatures for
     * arbitrary payloads is exactly the capability the secret is meant to gate.
     */
    @PostMapping("/email/sign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Compute the HMAC signature for a payload (ADMIN only)",
            description = "Demo aid for exercising POST /email with a genuine signature.")
    public Map<String, String> sign(@RequestBody byte[] rawBody) {
        if (!signatureVerifier.isConfigured()) {
            throw new WebhookNotConfiguredException();
        }
        return Map.of("X-Signature-256", signatureVerifier.sign(rawBody));
    }

    private EmailWebhookRequest parse(byte[] rawBody) {
        try {
            return objectMapper.readValue(new String(rawBody, StandardCharsets.UTF_8), EmailWebhookRequest.class);
        } catch (IOException e) {
            throw new com.careerpilot.common.error.BadRequestException(
                    "Webhook body was not valid JSON matching the expected email shape.");
        }
    }

    private static class InvalidSignatureException extends ApiException {
        InvalidSignatureException() {
            // 401, not 403: the caller failed to authenticate, and the message deliberately
            // does not distinguish "missing" from "wrong" -- that difference is only useful
            // to someone probing the endpoint.
            super(HttpStatus.UNAUTHORIZED, "Invalid webhook signature",
                    "Request signature missing or invalid.");
        }
    }

    private static class WebhookNotConfiguredException extends ApiException {
        WebhookNotConfiguredException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "Webhook not configured",
                    "No webhook secret is configured on this deployment.");
        }
    }
}
