package com.aiimpacteval.apicore.investment;

import com.aiimpacteval.apicore.investment.InvestmentProfileDtos.InvestmentProfileResponse;
import com.aiimpacteval.apicore.investment.InvestmentProfileDtos.LinkedPrsPage;
import com.aiimpacteval.apicore.security.ScopeResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Investment Profile tab (PRD E5). Under {@code /api/v1/metrics/**} — same prefix as Cockpit/
 * Code Review, so it inherits SecurityConfig's existing analytical-roles-only rule with no
 * security config change needed. Scope resolved server-side via {@link ScopeResolver}, same as
 * the other metrics endpoints — a MANAGER can't pass an arbitrary {@code scope} query param.
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class InvestmentProfileController {

    private static final int MAX_WINDOW_DAYS = 180;
    private static final int MAX_PAGE_SIZE = 100;

    private final InvestmentProfileQueryService queryService;
    private final ScopeResolver scopeResolver;

    public InvestmentProfileController(InvestmentProfileQueryService queryService, ScopeResolver scopeResolver) {
        this.queryService = queryService;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping("/investment-profile")
    public InvestmentProfileResponse investmentProfile(@RequestParam(defaultValue = "90") int days,
                                                       @RequestParam(defaultValue = "*") String scope) {
        int windowDays = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        return queryService.investmentProfile(windowDays, scopeResolver.resolve(scope));
    }

    /**
     * The per-PR verification drill-down (see class javadoc concern this answers: "is this PR
     * really matched to the right Jira ticket, or did the title-regex guess wrong"). Read-only —
     * there is no corresponding write endpoint, per the BRD's no-manual-tagging rule; this lets a
     * human verify the automatic match, not override it.
     */
    @GetMapping("/investment-profile/prs")
    public LinkedPrsPage investmentProfileLinkedPrs(@RequestParam(defaultValue = "90") int days,
                                                     @RequestParam(defaultValue = "*") String scope,
                                                     @RequestParam(required = false) String category,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize) {
        int windowDays = Math.min(Math.max(days, 1), MAX_WINDOW_DAYS);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return queryService.linkedPrs(windowDays, scopeResolver.resolve(scope), category, safePage, safePageSize);
    }
}