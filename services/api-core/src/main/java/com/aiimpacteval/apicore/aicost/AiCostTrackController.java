package com.aiimpacteval.apicore.aicost;

import com.aiimpacteval.apicore.aicost.AiCostTrackDtos.AiCostTrackResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Cost Track (PRD E9). Contract: openapi/api-core.yml. Under {@code /api/v1/metrics/**}, so
 * it inherits the existing analytical-role RBAC gate (ADR-0004) with no separate SecurityConfig
 * entry needed. Org-wide only for now — no team-scoped breakdown exists yet (see
 * {@link AiCostTrackDtos.AiCostTrackResponse}'s javadoc for why).
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class AiCostTrackController {

    private static final int MAX_WINDOW_DAYS = 90;

    private final AiCostTrackQueryService queryService;

    public AiCostTrackController(AiCostTrackQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/ai-cost-track")
    public AiCostTrackResponse aiCostTrack(@RequestParam(defaultValue = "30") int days) {
        int windowDays = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        return queryService.aiCostTrack(windowDays);
    }
}
