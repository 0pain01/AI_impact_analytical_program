package com.aiimpacteval.connector.jira.webhook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookTokenVerifierTest {

    @Test
    void acceptsMatchingToken() {
        assertTrue(WebhookTokenVerifier.isValid("secret-token", "secret-token"));
    }

    @Test
    void rejectsWrongToken() {
        assertFalse(WebhookTokenVerifier.isValid("secret-token", "other-token"));
        assertFalse(WebhookTokenVerifier.isValid("secret-token", "secret-token2"));
    }

    @Test
    void rejectsMissingToken() {
        assertFalse(WebhookTokenVerifier.isValid("secret-token", null));
        assertFalse(WebhookTokenVerifier.isValid("secret-token", ""));
    }

    @Test
    void failsClosedWhenSecretUnconfigured() {
        assertFalse(WebhookTokenVerifier.isValid("", "anything"));
        assertFalse(WebhookTokenVerifier.isValid(null, "anything"));
    }
}
