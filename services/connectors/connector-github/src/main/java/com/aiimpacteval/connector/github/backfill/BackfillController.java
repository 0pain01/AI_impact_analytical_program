package com.aiimpacteval.connector.github.backfill;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
