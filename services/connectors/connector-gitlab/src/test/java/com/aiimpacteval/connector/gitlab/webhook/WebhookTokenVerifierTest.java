package com.aiimpacteval.connector.gitlab.webhook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookTokenVerifierTest {

    private static final String SECRET = "test-secret";

    @Test
    void acceptsMatchingToken() {
        assertTrue(WebhookTokenVerifier.isValid(SECRET, SECRET));
    }

    @Test
    void rejectsWrongToken() {
        assertFalse(WebhookTokenVerifier.isValid(SECRET, "wrong-secret"));
    }

    @Test
    void rejectsMissingToken() {
        assertFalse(WebhookTokenVerifier.isValid(SECRET, null));
    }

    @Test
    void rejectsWhenSecretUnconfigured() {
        assertFalse(WebhookTokenVerifier.isValid("", SECRET));
        assertFalse(WebhookTokenVerifier.isValid(null, SECRET));
    }
}
