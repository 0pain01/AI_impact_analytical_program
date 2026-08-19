package com.aiimpacteval.apicore.investment;

import com.aiimpacteval.apicore.investment.InvestmentProfileDtos.InvestmentProfileResponse;
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
}