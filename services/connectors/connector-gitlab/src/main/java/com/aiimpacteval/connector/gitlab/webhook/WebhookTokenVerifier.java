package com.aiimpacteval.connector.gitlab.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * GitLab webhooks are not HMAC-signed like GitHub's: the secret token configured on the
 * webhook (Settings &gt; Webhooks &gt; Secret token) is sent back verbatim as the
 * {@code X-Gitlab-Token} header. Comparison is constant-time. An unconfigured secret rejects
 * everything — fail closed.
 */
public final class WebhookTokenVerifier {

    private WebhookTokenVerifier() {
    }

    public static boolean isValid(String configuredSecret, String providedToken) {
        if (configuredSecret == null || configuredSecret.isBlank() || providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configuredSecret.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
