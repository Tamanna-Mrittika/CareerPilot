package com.careerpilot.tracker.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verifies the HMAC-SHA256 signature on inbound email webhooks.
 *
 * <p>The webhook endpoint cannot use the JWT the rest of the system runs on: the caller is
 * a mail provider, not a logged-in user, and there is no user session to carry. A shared
 * secret plus a signature over the exact request body is the standard answer (GitHub,
 * Stripe and Mailgun all do this), and it is what makes an unauthenticated public endpoint
 * safe to expose -- without it, anyone who found the URL could move other people's
 * applications around.
 *
 * <p>Two details that are easy to get wrong and are the whole point of the control:
 * <ul>
 *   <li>The signature is computed over the <strong>raw body bytes</strong>, before any JSON
 *       parsing. Re-serialising the parsed object first would change whitespace and key
 *       order, and the signature would never match.</li>
 *   <li>Comparison uses {@link MessageDigest#isEqual}, which is constant-time. A plain
 *       {@code equals} returns as soon as it finds a differing byte, which leaks how much
 *       of a guessed signature was correct and makes the secret brute-forceable one byte
 *       at a time.</li>
 * </ul>
 */
@Component
@Slf4j
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PREFIX = "sha256=";

    private final String secret;

    public WebhookSignatureVerifier(@Value("${careerpilot.webhook.secret:}") String secret) {
        this.secret = secret;
        if (!StringUtils.hasText(secret)) {
            log.warn("""
                    No webhook secret configured (careerpilot.webhook.secret).
                    The email webhook will REJECT every request until one is set -- failing
                    closed rather than accepting unsigned input from the open internet.""");
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(secret);
    }

    /**
     * @param rawBody         the exact bytes received, unparsed
     * @param signatureHeader value of X-Signature-256, expected as "sha256=<hex>"
     */
    public boolean verify(byte[] rawBody, String signatureHeader) {
        // Fail closed: an unconfigured secret must never mean "allow everything".
        if (!isConfigured() || !StringUtils.hasText(signatureHeader) || rawBody == null) {
            return false;
        }

        String provided = signatureHeader.trim();
        if (provided.toLowerCase(Locale.ENGLISH).startsWith(PREFIX)) {
            provided = provided.substring(PREFIX.length());
        }

        byte[] expected = computeHmac(rawBody);
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(provided.toLowerCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            // Malformed hex is just an invalid signature, not a server error.
            return false;
        }

        return MessageDigest.isEqual(expected, actual);
    }

    /** Exposed so the ADMIN-only simulate endpoint can show a caller how to sign a payload. */
    public String sign(byte[] rawBody) {
        return PREFIX + HexFormat.of().formatHex(computeHmac(rawBody));
    }

    private byte[] computeHmac(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable or key invalid", e);
        }
    }
}
