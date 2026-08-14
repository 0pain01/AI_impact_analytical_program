package com.aiimpacteval.apicore.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Write side of team structure administration. Complements {@code TeamQueryService} (read side)
 * and {@code identity-service}'s {@code TeamImportService} (auto-import from a real org's
 * GitHub Teams API).
 *
 * <p>Teams created here are tagged {@code source = 'manual'} so they never collide with rows a
 * future/real GitHub team sync would upsert under {@code source = 'github'} — same
 * {@code (source, source_id)} uniqueness convention identity-service already relies on. This
 * exists precisely so org structure can be modeled by hand today (an admin knows their real team
 * boundaries even before/without GitHub Teams being wired up) and swapped for automatic sync
 * later without a data migration: both paths write the same {@code core.team}/{@code
 * core.team_repo} tables metrics-engine already rolls up from.
 */
@Service
public class TeamAdminService {

    private final JdbcTemplate jdbcTemplate;

    public TeamAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a team, or updates it in place if one with the same (derived) slug already exists —
     * safe to call again when renaming/re-parenting rather than accumulating duplicates.
     */
    public UUID upsertTeam(String name, UUID parentTeamId) {
        String slug = slugify(name);
        return jdbcTemplate.queryForObject("""
                        INSERT INTO core.team (name, parent_team_id, source, source_id)
                        VALUES (?, ?, 'manual', ?)
                        ON CONFLICT (source, source_id)
                        DO UPDATE SET name = excluded.name, parent_team_id = excluded.parent_team_id
                        RETURNING id
                        """,
                UUID.class, name, parentTeamId, slug);
    }

    /** Idempotent — mapping the same repo to the same team twice is a no-op, not an error. */
    public void addRepo(UUID teamId, String repo) {
        jdbcTemplate.update("""
                INSERT INTO core.team_repo (team_id, repo) VALUES (?, ?)
                ON CONFLICT (team_id, repo) DO NOTHING
                """, teamId, repo);
    }

    public void removeRepo(UUID teamId, String repo) {
        jdbcTemplate.update("DELETE FROM core.team_repo WHERE team_id = ? AND repo = ?", teamId, repo);
    }

    public List<String> listRepos(UUID teamId) {
        return jdbcTemplate.queryForList(
                "SELECT repo FROM core.team_repo WHERE team_id = ? ORDER BY repo", String.class, teamId);
    }

    private String slugify(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}