package com.aiimpacteval.apicore.security;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in {@code core.app_user} — the real identity behind a login. {@code teamId} is the
 * scope pin for MANAGER accounts (null for every other role — see {@link ScopeResolver}).
 * {@code githubLogin} links this account to their GitHub username so the Personal Activity tab
 * (IC role) can self-scope against {@code staging.pull_request_state} / {@code
 * pull_request_review_state}, which are keyed by GitHub identity, not by {@code app_user.email}.
 * Null until an admin sets it (e.g. a fresh IC account has nothing to show until then).
 */
public record AppUser(UUID id, String email, String displayName, Role role, UUID teamId,
                      String githubLogin, boolean active, Instant lastLoginAt) {
}