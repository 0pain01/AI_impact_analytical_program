package com.aiimpacteval.connector.github.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Bounded-retry GitHub API fetch shared by the backfill services — vendor errors must not
 * abort a backfill silently (FR-1.8).
 */
final class RetryingJsonFetcher {

    private static final Logger log = LoggerFactory.getLogger(RetryingJsonFetcher.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;

    RetryingJsonFetcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode fetch(Supplier<String> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                String body = call.get();
                return body == null ? null : objectMapper.readTree(body);
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("GitHub API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                long backoffMillis = Duration.ofSeconds(2L * attempt).toMillis();
                log.warn("GitHub API call failed (attempt {}/{}), retrying in {} ms: {}",
                        attempt, MAX_ATTEMPTS, backoffMillis, e.getMessage());
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new BackfillException("Backfill interrupted", interrupted);
                }
            }
        }
    }

    static class BackfillException extends RuntimeException {
        BackfillException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
