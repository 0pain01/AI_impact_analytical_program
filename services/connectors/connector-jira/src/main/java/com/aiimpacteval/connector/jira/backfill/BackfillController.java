package com.aiimpacteval.connector.jira.backfill;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal trigger, not exposed publicly. Invoked by api-core when a Jira project is
 * connected; direct call supports pilot onboarding and debugging.
 */
@RestController
public class BackfillController {

    private final JiraBackfillService backfillService;

    public BackfillController(JiraBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/internal/backfill")
    public JiraBackfillService.BackfillResult backfill(@RequestParam String projectKey) {
        return backfillService.backfillProject(projectKey);
    }
}
