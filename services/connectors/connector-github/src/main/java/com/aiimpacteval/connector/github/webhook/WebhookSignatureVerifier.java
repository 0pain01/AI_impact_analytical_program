package com.aiimpacteval.connector.github.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies GitHub's {@code X-Hub-Signature-256} header: {@code sha256=<hex hmac>} of the raw
 * request body, keyed with the webhook secret. Comparison is constant-time.
 */
public final class WebhookSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private WebhookSignatureVerifier() {
    }

    public static boolean isValid(String secret, byte[] rawBody, String signatureHeader) {
        if (secret == null || secret.isBlank()
                || signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] expected = hmacSha256(secret, rawBody);
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException malformedHex) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    private static byte[] hmacSha256(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
