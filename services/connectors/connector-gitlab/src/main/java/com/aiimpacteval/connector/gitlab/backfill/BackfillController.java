package com.aiimpacteval.connector.gitlab.backfill;

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
 * when a project is connected; direct call supports pilot onboarding and debugging.
 */
@RestController
public class BackfillController {

    private final GitlabBackfillService backfillService;
    private final GitlabGroupBackfillService groupBackfillService;

    public BackfillController(GitlabBackfillService backfillService,
                              GitlabGroupBackfillService groupBackfillService) {
        this.backfillService = backfillService;
        this.groupBackfillService = groupBackfillService;
    }

    @PostMapping("/internal/backfill")
    public GitlabBackfillService.BackfillResult backfill(@RequestParam String project) {
        return backfillService.backfillProject(project);
    }

    @PostMapping("/internal/backfill-groups")
    public GitlabGroupBackfillService.GroupBackfillResult backfillGroups(@RequestParam String group) {
        return groupBackfillService.backfillGroup(group);
    }

    /**
     * Without this, an exhausted GitLab rate limit surfaced as Spring's generic whitelabel
     * 500 — indistinguishable from a real bug, same problem connector-github hit and fixed for
     * GitHub's rate limiting. 429 + a reset time lets the Admin UI (and whoever's debugging a
     * "why won't this project sync" report) tell "wait and retry" apart from "something is
     * actually broken."
     */
    @ExceptionHandler(RetryingJsonFetcher.RateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RetryingJsonFetcher.RateLimitException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "gitlab_rate_limited");
        body.put("message", e.getMessage()
                + " — export GITLAB_TOKEN before starting connector-gitlab (see its README) to"
                + " raise the limit above the unauthenticated default.");
        if (e.resetAt != null) {
            body.put("retryAfter", e.resetAt.toString());
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }
}
