package com.aiimpacteval.connector.gitlab.backfill;

import com.aiimpacteval.common.http.TimeoutRestClients;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Shared GitLab REST client setup for the backfill services. */
final class GitlabRestClients {

    // Same rationale as connector-github's GithubRestClients: an unbounded call can hang a
    // backfill (and the Admin console's sync-status tracker with it) forever on a dropped
    // connection. Bounded here so RetryingJsonFetcher's retry loop actually gets a chance to run.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private GitlabRestClients() {
    }

    static RestClient build(RestClient.Builder builder, String apiBaseUrl, String token) {
        return TimeoutRestClients.withTimeouts(builder, CONNECT_TIMEOUT, READ_TIMEOUT)
                .baseUrl(apiBaseUrl)
                .defaultHeaders(headers -> {
                    if (token != null && !token.isBlank()) {
                        // GitLab personal/project/group access tokens authenticate via
                        // PRIVATE-TOKEN, not Authorization: Bearer.
                        headers.set("PRIVATE-TOKEN", token);
                    }
                })
                .build();
    }
}
