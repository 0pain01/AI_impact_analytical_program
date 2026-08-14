package com.aiimpacteval.connector.jira.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Jira webhooks are not HMAC-signed; the webhook URL configured in Jira carries a shared
 * secret token ({@code ?token=...}). Comparison is constant-time. An unconfigured secret
 * rejects everything — fail closed.
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
