package com.aiimpacteval.apicore.teams;

import com.aiimpacteval.apicore.security.ScopeResolver;
import com.aiimpacteval.apicore.teams.TeamQueryService.TeamSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Team listing for the Cockpit org → team drill-down picker (PRD E4-S2). Scope-resolved like
 * Cockpit/CodeReview: a MANAGER only gets their own team back, not the full list — previously
 * every caller saw every team, so a MANAGER could click into any team's card and would always
 * land on their own team's data regardless (the metrics endpoints were already enforcing the
 * pin correctly; the picker just didn't reflect it).
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamQueryService queryService;
    private final ScopeResolver scopeResolver;

    public TeamController(TeamQueryService queryService, ScopeResolver scopeResolver) {
        this.queryService = queryService;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping
    public List<TeamSummary> listTeams() {
        return queryService.listTeams(scopeResolver.resolve("*"));
    }
}