package com.aiimpacteval.connector.github.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private static final byte[] BODY = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsValidSignature() {
        assertTrue(WebhookSignatureVerifier.isValid(SECRET, BODY, sign(SECRET, BODY)));
    }

    @Test
    void rejectsSignatureFromWrongSecret() {
        assertFalse(WebhookSignatureVerifier.isValid(SECRET, BODY, sign("other-secret", BODY)));
    }

    @Test
    void rejectsTamperedBody() {
        byte[] tampered = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(WebhookSignatureVerifier.isValid(SECRET, tampered, sign(SECRET, BODY)));
    }

    @Test
    void rejectsMissingOrMalformedHeader() {
        assertFalse(WebhookSignatureVerifier.isValid(SECRET, BODY, null));
        assertFalse(WebhookSignatureVerifier.isValid(SECRET, BODY, "sha1=abcdef"));
        assertFalse(WebhookSignatureVerifier.isValid(SECRET, BODY, "sha256=not-hex!"));
    }

    @Test
    void rejectsWhenSecretUnconfigured() {
        assertFalse(WebhookSignatureVerifier.isValid("", BODY, sign(SECRET, BODY)));
        assertFalse(WebhookSignatureVerifier.isValid(null, BODY, sign(SECRET, BODY)));
    }

    private static String sign(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
