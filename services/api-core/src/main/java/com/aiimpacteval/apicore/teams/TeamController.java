package com.aiimpacteval.apicore.teams;

import com.aiimpacteval.apicore.teams.TeamQueryService.TeamSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Team listing for the Cockpit org → team drill-down picker (PRD E4-S2). */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamQueryService queryService;

    public TeamController(TeamQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<TeamSummary> listTeams() {
        return queryService.listTeams();
    }
}
