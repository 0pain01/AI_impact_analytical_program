package com.aiimpacteval.apicore.metrics;

import com.aiimpacteval.apicore.metrics.CockpitDtos.CockpitResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cockpit tiles (PRD E4-S1/E4-S2). Contract: openapi/api-core.yml. RBAC is enforced
 * (ADR-0004): analytical roles only. Per-user team/individual visibility restriction (beyond
 * role) is not yet applied — any analytical-role caller can pass any {@code scope}; tracked in
 * PRD Appendix B alongside E8-S2's opt-in gate for individual-level views.
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class CockpitController {

    private static final int MAX_WINDOW_DAYS = 90;

    private final CockpitQueryService queryService;

    public CockpitController(CockpitQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/cockpit")
    public CockpitResponse cockpit(@RequestParam(defaultValue = "30") int days,
                                   @RequestParam(defaultValue = "*") String scope) {
        int windowDays = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        return queryService.cockpit(windowDays, scope);
    }
}
