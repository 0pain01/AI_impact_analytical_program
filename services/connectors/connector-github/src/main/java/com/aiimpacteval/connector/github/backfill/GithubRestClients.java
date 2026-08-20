package com.aiimpacteval.connector.github.backfill;

import com.aiimpacteval.common.http.TimeoutRestClients;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Shared GitHub REST client setup for the backfill services. */
final class GithubRestClients {

    // A dropped connection mid-request used to hang this call forever (no default timeout) —
    // one paginated call getting stuck this way stalled the entire repo backfill, and with it
    // the Admin console's sync-status tracker, indefinitely. Bounded here so a stuck call fails
    // within a call, letting RetryingJsonFetcher's existing 3-attempt retry actually kick in
    // instead of never getting the chance to.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private GithubRestClients() {
    }

    static RestClient build(RestClient.Builder builder, String apiBaseUrl, String token) {
        return TimeoutRestClients.withTimeouts(builder, CONNECT_TIMEOUT, READ_TIMEOUT)
                .baseUrl(apiBaseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeaders(headers -> {
                    if (token != null && !token.isBlank()) {
                        headers.setBearerAuth(token);
                    }
                })
                .build();
    }
}
