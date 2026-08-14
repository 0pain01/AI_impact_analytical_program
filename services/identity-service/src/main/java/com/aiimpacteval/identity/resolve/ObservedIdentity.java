package com.aiimpacteval.identity.resolve;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * One identity observation extracted from a tool event: who a source system says acted.
 * {@code sourceUserId} is the stable per-tool identifier (GitHub numeric user id, Jira
 * accountId, or {@code email:<address>} when the tool exposes only an email).
 */
public record ObservedIdentity(String source, String sourceUserId, String name, String email) {

    private static final Set<String> KNOWN_BOTS =
            Set.of("dependabot", "renovate", "github-actions", "copilot");

    public ObservedIdentity {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceUserId, "sourceUserId");
    }

    public String normalizedEmail() {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Bot heuristic (PRD F4/E2): excluded from people-metrics by default. */
    public boolean isBot() {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String e = normalizedEmail() == null ? "" : normalizedEmail();
        return n.endsWith("[bot]")
                || KNOWN_BOTS.stream().anyMatch(bot -> n.startsWith(bot))
                || e.endsWith("@users.noreply.github.com") && n.endsWith("[bot]")
                || e.contains("noreply") && n.contains("bot");
    }
}
