package com.aiimpacteval.common.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Applies a bounded connect/read timeout to a {@link RestClient.Builder}. Every connector's
 * outbound vendor client (GitHub, Jira, Jenkins) and api-core's connector-admin client used to
 * build with no timeout at all — Spring's default {@code ClientHttpRequestFactory} has none —
 * so a dropped connection mid-request left the call blocked indefinitely rather than failing:
 * the whole backfill (and the Admin console's sync-status tracker, which is just waiting on that
 * same blocked HTTP call) got stuck showing "Syncing" forever instead of surfacing a real
 * failure the existing retry logic could act on. Found via a real repo connect that hung after
 * the user's internet dropped mid-sync.
 */
public final class TimeoutRestClients {

    private TimeoutRestClients() {
    }

    public static RestClient.Builder withTimeouts(RestClient.Builder builder, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return builder.requestFactory(factory);
    }
}
