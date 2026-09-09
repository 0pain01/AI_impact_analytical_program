package com.aiimpacteval.connector.gitlab.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Bounded-retry GitLab API fetch shared by the backfill services — vendor errors must not
 * abort a backfill silently (FR-1.8).
 */
final class RetryingJsonFetcher {

    private static final Logger log = LoggerFactory.getLogger(RetryingJsonFetcher.class);
    private static final int MAX_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    RetryingJsonFetcher(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    JsonNode fetch(Supplier<String> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                String body = call.get();
                return body == null ? null : objectMapper.readTree(body);
            } catch (HttpClientErrorException e) {
                // Unlike GitHub (which overloads 403 for an exhausted rate limit), GitLab
                // signals it directly with HTTP 429 — no need to inspect a remaining-quota
                // header to tell it apart from a genuine auth/permission failure. The window
                // doesn't clear within this method's few-second retry budget, so fail fast with
                // the reset time instead of burning attempts on the same 429.
                if (e.getStatusCode() == HttpStatusCode.valueOf(429)) {
                    throw asRateLimit(e);
                }
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("GitLab API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                backoffThenRetry(attempt, e);
            } catch (Exception e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BackfillException("GitLab API call failed after " + MAX_ATTEMPTS + " attempts", e);
                }
                backoffThenRetry(attempt, e);
            }
        }
    }

    private void backoffThenRetry(int attempt, Exception e) {
        long backoffMillis = Duration.ofSeconds(2L * attempt).toMillis();
        log.warn("GitLab API call failed (attempt {}/{}), retrying in {} ms: {}",
                attempt, MAX_ATTEMPTS, backoffMillis, e.getMessage());
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BackfillException("Backfill interrupted", interrupted);
        }
    }

    /**
     * GitLab returns {@code Retry-After} (seconds) on a 429; some deployments instead/also
     * send {@code RateLimit-Reset} (epoch seconds). Prefer Retry-After since it's the one
     * GitLab.com actually documents for the REST API.
     * See https://docs.gitlab.com/ee/user/gitlab_com/#gitlabcom-specific-rate-limits
     */
    private RateLimitException asRateLimit(HttpClientErrorException e) {
        HttpHeaders headers = e.getResponseHeaders();
        Instant resetAt = null;
        if (headers != null) {
            String retryAfter = headers.getFirst("Retry-After");
            String rateLimitReset = headers.getFirst("RateLimit-Reset");
            if (retryAfter != null) {
                resetAt = Instant.now(clock).plusSeconds(Long.parseLong(retryAfter));
            } else if (rateLimitReset != null) {
                resetAt = Instant.ofEpochSecond(Long.parseLong(rateLimitReset));
            }
        }
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
            super("GitLab API rate limit exhausted"
                    + (resetAt != null ? " — resets at " + resetAt : " (no reset time reported)"), cause);
            this.resetAt = resetAt;
        }
    }
}
