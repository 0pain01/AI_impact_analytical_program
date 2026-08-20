package com.aiimpacteval.connector.jenkins.backfill;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal trigger, not exposed publicly. Invoked by api-core when a Jenkins job is connected;
 * direct call supports pilot onboarding and debugging (same shape as connector-jira's).
 */
@RestController
public class BackfillController {

    private final JenkinsBackfillService backfillService;

    public BackfillController(JenkinsBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/internal/backfill")
    public JenkinsBackfillService.BackfillResult backfill(@RequestParam String jobName) {
        return backfillService.backfillJob(jobName);
    }
}
