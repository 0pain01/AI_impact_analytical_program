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

    /**
     * @param scope {@code "*"} for every team, or a single team UUID to return just that one.
     *              Previously this had no scope param at all — every caller got every team,
     *              which is how a MANAGER pinned to one team could still see (and click into)
     *              every other team's card, even though the metrics endpoints would silently
     *              override that click back to their own team. Now the list itself matches what
     *              they're actually allowed to see.
     */
    public List<TeamSummary> listTeams(String scope) {
        boolean orgWide = scope == null || "*".equals(scope);
        return jdbcTemplate.query("""
                        SELECT t.id, t.name, count(tr.repo) AS repo_count
                        FROM core.team t
                        LEFT JOIN core.team_repo tr ON tr.team_id = t.id
                        WHERE (?::text IS NULL OR t.id = ?::uuid)
                        GROUP BY t.id, t.name
                        ORDER BY t.name
                        """,
                (rs, rowNum) -> new TeamSummary(
                        (UUID) rs.getObject("id"), rs.getString("name"), rs.getInt("repo_count")),
                orgWide ? null : scope, orgWide ? null : scope);
    }

    public record TeamSummary(UUID id, String name, int repoCount) {
    }
}