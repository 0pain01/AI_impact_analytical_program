package com.aiimpacteval.apicore.codereview;

import com.aiimpacteval.apicore.codereview.CodeReviewDtos.CodeReviewResponse;
import com.aiimpacteval.apicore.security.ScopeResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Code Review tab (PRD PG-4). Deliberately under {@code /api/v1/metrics/**} — same route prefix
 * as Cockpit, so it falls under SecurityConfig's existing analytical-roles-only rule with no
 * security config change needed. Scope is resolved server-side via {@link ScopeResolver}, same
 * as Cockpit — a MANAGER can no longer pass an arbitrary {@code scope} query param.
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class CodeReviewController {

    private static final int MAX_WINDOW_DAYS = 90;
    private static final int MAX_PAGE_SIZE = 100;

    private final CodeReviewQueryService queryService;
    private final ScopeResolver scopeResolver;

    public CodeReviewController(CodeReviewQueryService queryService, ScopeResolver scopeResolver) {
        this.queryService = queryService;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping("/code-review")
    public CodeReviewResponse codeReview(@RequestParam(defaultValue = "30") int days,
                                         @RequestParam(defaultValue = "*") String scope,
                                         @RequestParam(required = false) String repo,
                                         @RequestParam(defaultValue = "age") String sortBy,
                                         @RequestParam(defaultValue = "desc") String sortDir,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        int windowDays = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        int safePage = Math.max(page, 0);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        String resolvedScope = scopeResolver.resolve(scope);
        return queryService.codeReview(windowDays, resolvedScope, repo, sortBy, sortDir, safePage, safePageSize);
    }
}