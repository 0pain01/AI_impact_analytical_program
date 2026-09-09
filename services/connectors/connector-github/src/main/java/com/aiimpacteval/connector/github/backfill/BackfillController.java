package com.aiimpacteval.connector.github.backfill;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal trigger, not exposed publicly. In the full Admin flow this is invoked by api-core
 * when a repository is connected; direct call supports pilot onboarding and debugging.
 */
@RestController
public class BackfillController {

    private final GithubBackfillService backfillService;
    private final GithubTeamBackfillService teamBackfillService;

    public BackfillController(GithubBackfillService backfillService,
                              GithubTeamBackfillService teamBackfillService) {
        this.backfillService = backfillService;
        this.teamBackfillService = teamBackfillService;
    }

    @PostMapping("/internal/backfill")
    public GithubBackfillService.BackfillResult backfill(@RequestParam String owner,
                                                         @RequestParam String repo) {
        return backfillService.backfillRepository(owner, repo);
    }

    @PostMapping("/internal/backfill-teams")
    public GithubTeamBackfillService.TeamBackfillResult backfillTeams(@RequestParam String org) {
        return teamBackfillService.backfillOrgTeams(org);
    }

    /**
     * Without this, an exhausted GitHub rate limit surfaced as Spring's generic whitelabel 500 —
     * indistinguishable from a real bug, and api-core's Admin console (ConnectorAdminService)
     * just relayed that opaque text as the repo's syncError. 429 + a reset time lets the Admin
     * UI (and whoever's debugging a "why won't this repo sync" report) tell "wait and retry"
     * apart from "something is actually broken." No GITHUB_TOKEN configured (dev default) means
     * the unauthenticated 60-req/hour cap is trivial to exhaust — see GithubRestClients.
     */
    @ExceptionHandler(RetryingJsonFetcher.RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RetryingJsonFetcher.RateLimitException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "github_rate_limited");
        body.put("message", e.getMessage()
                + " — export GITHUB_TOKEN before starting connector-github (see its README) to raise"
                + " the limit from 60/hour to 5,000/hour.");
        if (e.resetAt != null) {
            body.put("retryAfter", e.resetAt.toString());
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }
}
