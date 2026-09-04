package com.aiimpacteval.connector.github.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
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
            } catch (HttpClientErrorException e) {
                // An exhausted rate limit doesn't clear within this method's few-second retry
                // window (window resets hourly), so retrying here just burns attempts and turns
                // into the same opaque failure. Fail fast with the reset time instead so the
                // caller (BackfillController) can surface a clear, actionable error rather than
                // a generic 500.
                RateLimitException rateLimit = asRateLimit(e);
                if (rateLimit != null) {
                    throw rateLimit;
                }
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("GitHub API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                backoffThenRetry(attempt, e);
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("GitHub API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                backoffThenRetry(attempt, e);
            }
        }
    }

    private void backoffThenRetry(int attempt, Exception e) {
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

    /**
     * GitHub signals an exhausted rate limit (as opposed to a genuine auth/permission 403) via
     * {@code X-RateLimit-Remaining: 0} on a 403/429 response, with {@code X-RateLimit-Reset}
     * giving the epoch-seconds reset time. See
     * https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api
     */
    private static RateLimitException asRateLimit(HttpClientErrorException e) {
        HttpHeaders headers = e.getResponseHeaders();
        if (headers == null || !"0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
            return null;
        }
        String resetHeader = headers.getFirst("X-RateLimit-Reset");
        Instant resetAt = resetHeader == null ? null : Instant.ofEpochSecond(Long.parseLong(resetHeader));
        return new RateLimitException(resetAt, e);
    }

    static class BackfillException extends RuntimeException {
        BackfillException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Distinguishable from a generic {@link BackfillException} so callers can respond 429, not 500. */
    static class RateLimitException extends RuntimeException {
        final Instant resetAt;

        RateLimitException(Instant resetAt, Throwable cause) {
            super("GitHub API rate limit exhausted"
                    + (resetAt != null ? " — resets at " + resetAt : " (no reset time reported)"), cause);
            this.resetAt = resetAt;
        }
    }
}
