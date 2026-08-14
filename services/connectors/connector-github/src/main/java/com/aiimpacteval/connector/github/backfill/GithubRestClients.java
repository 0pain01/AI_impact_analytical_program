package com.aiimpacteval.connector.github.backfill;

import org.springframework.web.client.RestClient;

/** Shared GitHub REST client setup for the backfill services. */
final class GithubRestClients {

    private GithubRestClients() {
    }

    static RestClient build(RestClient.Builder builder, String apiBaseUrl, String token) {
        return builder
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
