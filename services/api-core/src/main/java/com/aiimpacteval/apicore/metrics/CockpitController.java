package com.aiimpacteval.apicore.metrics;

import com.aiimpacteval.apicore.metrics.CockpitDtos.CockpitResponse;
import com.aiimpacteval.apicore.security.ScopeResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cockpit tiles (PRD E4-S1/E4-S2). Contract: openapi/api-core.yml. RBAC is enforced
 * (ADR-0004): analytical roles only, and — via {@link ScopeResolver} — team-scoped roles
 * (MANAGER) can no longer pass an arbitrary {@code scope}; it's pinned server-side to their
 * {@code core.app_user.team_id}. Previously any analytical-role caller could pass any scope;
 * see PRD Appendix B / E8-S2.
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class CockpitController {

    private static final int MAX_WINDOW_DAYS = 90;

    private final CockpitQueryService queryService;
    private final ScopeResolver scopeResolver;

    public CockpitController(CockpitQueryService queryService, ScopeResolver scopeResolver) {
        this.queryService = queryService;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping("/cockpit")
    public CockpitResponse cockpit(@RequestParam(defaultValue = "30") int days,
                                   @RequestParam(defaultValue = "*") String scope) {
        int windowDays = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        return queryService.cockpit(windowDays, scopeResolver.resolve(scope));
    }
}