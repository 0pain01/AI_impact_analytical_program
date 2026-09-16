package com.aiimpacteval.apicore.jira;

import com.aiimpacteval.apicore.jira.JiraDashboardDtos.JiraDashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jira Work Items dashboard. Deliberately under {@code /api/v1/metrics/**} — same route prefix as
 * Cockpit/Code Review, so it falls under SecurityConfig's existing analytical-roles-only rule
 * with no security config change needed. {@code project} is a Jira project key or {@code "*"} for
 * every connected project — unlike Cockpit/Code Review's {@code scope}, this is never a team
 * UUID: no project-to-team mapping exists yet (see docs/01-product/functional-specification.md).
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class JiraDashboardController {

    private static final int MAX_WINDOW_DAYS = 90;
    private static final int MAX_PAGE_SIZE = 100;

    private final JiraDashboardQueryService queryService;

    public JiraDashboardController(JiraDashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/jira-work-items")
    public JiraDashboardResponse jiraWorkItems(@RequestParam(defaultValue = "30") int days,
                                               @RequestParam(defaultValue = "*") String project,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "age") String sortBy,
                                               @RequestParam(defaultValue = "desc") String sortDir,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        int windowDays = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        int safePage = Math.max(page, 0);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        return queryService.jiraWorkItems(windowDays, project, q, sortBy, sortDir, safePage, safePageSize);
    }
}
