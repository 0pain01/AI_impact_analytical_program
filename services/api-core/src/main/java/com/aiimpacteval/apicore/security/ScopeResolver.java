package com.aiimpacteval.apicore.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the scope a metrics query should actually run with, given who's asking (PRD
 * E8-S1/S2). Closes the gap CockpitController's javadoc previously flagged: "any analytical-role
 * caller can pass any scope."
 *
 * <p>ADMIN, FINANCE_READONLY, and ENG_LEADER (the CTO/VP Engineering persona — see
 * {@code Login.tsx}'s role list) are org-wide by role: they get whatever {@code scope} they
 * asked for (defaulting to {@code "*"}). MANAGER (Engineering Manager persona, "Team scope" per
 * the PRD persona table) is pinned to the {@code scope} claim on their JWT — their
 * {@code core.app_user.team_id}, set by an admin — regardless of what the client requests. This
 * is the real enforcement point, not just what the UI happens to send.
 */
@Component
public class ScopeResolver {

    public String resolve(String requestedScope) {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String role = jwt.getClaimAsString("role");

        boolean orgWideByRole = Role.ADMIN.name().equals(role)
                || Role.FINANCE_READONLY.name().equals(role)
                || Role.ENG_LEADER.name().equals(role);
        if (orgWideByRole) {
            return (requestedScope == null || requestedScope.isBlank()) ? "*" : requestedScope;
        }

        // MANAGER (the only team-pinned role that reaches this endpoint — IC is denied at the
        // SecurityConfig route level and never reaches here at all).
        String tokenScope = jwt.getClaimAsString("scope");
        return (tokenScope == null || tokenScope.isBlank()) ? "*" : tokenScope;
    }
}