package com.aiimpacteval.apicore.teams;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Read side of team structure (E2-S2/E4-S2) — lists teams for the Cockpit drill-down picker. */
@Service
public class TeamQueryService {

    private final JdbcTemplate jdbcTemplate;

    public TeamQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TeamSummary> listTeams() {
        return jdbcTemplate.query("""
                        SELECT t.id, t.name, count(tr.repo) AS repo_count
                        FROM core.team t
                        LEFT JOIN core.team_repo tr ON tr.team_id = t.id
                        GROUP BY t.id, t.name
                        ORDER BY t.name
                        """,
                (rs, rowNum) -> new TeamSummary(
                        (UUID) rs.getObject("id"), rs.getString("name"), rs.getInt("repo_count")));
    }

    public record TeamSummary(UUID id, String name, int repoCount) {
    }
}
