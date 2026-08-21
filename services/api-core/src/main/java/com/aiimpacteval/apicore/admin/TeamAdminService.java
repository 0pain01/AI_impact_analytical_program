package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.audit.AuditLog;
import com.aiimpacteval.apicore.audit.AuditLog.AuditEvent;
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
    private final AuditLog auditLog;

    public TeamAdminService(JdbcTemplate jdbcTemplate, AuditLog auditLog) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLog = auditLog;
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

    /**
     * Blocks rather than silently cascading when the team is still load-bearing: a MANAGER
     * account pinned to it via {@code core.app_user.team_id} would otherwise fall back to
     * org-wide scope on their next login (see {@code ScopeResolver}) — a silent privilege
     * change, not something a team delete should cause as a side effect. Same reasoning for
     * sub-teams via {@code parent_team_id}. Repo/member mappings are safe to cascade — they're
     * just this team's own rows, not another entity's access grant.
     */
    public void deleteTeam(String actorEmail, UUID teamId, String sourceIp) {
        String name = jdbcTemplate.query("SELECT name FROM core.team WHERE id = ?",
                        (rs, rowNum) -> rs.getString("name"), teamId)
                .stream().findFirst()
                .orElseThrow(() -> new NoSuchTeamException(teamId));

        Integer pinnedUsers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM core.app_user WHERE team_id = ?", Integer.class, teamId);
        if (pinnedUsers != null && pinnedUsers > 0) {
            throw new TeamHasDependentsException(pinnedUsers + " user account(s) are pinned to this team "
                    + "— reassign them in User Management before deleting it.");
        }
        Integer childTeams = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM core.team WHERE parent_team_id = ?", Integer.class, teamId);
        if (childTeams != null && childTeams > 0) {
            throw new TeamHasDependentsException(childTeams + " sub-team(s) reference this team as their "
                    + "parent — delete or reparent them first.");
        }

        Integer repoCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM core.team_repo WHERE team_id = ?", Integer.class, teamId);

        jdbcTemplate.update("DELETE FROM core.team_repo WHERE team_id = ?", teamId);
        jdbcTemplate.update("DELETE FROM core.team_member WHERE team_id = ?", teamId);
        jdbcTemplate.update("DELETE FROM core.team WHERE id = ?", teamId);

        auditLog.write(new AuditEvent(actorEmail, "TEAM_DELETED", "team", teamId.toString(),
                "{\"name\":\"" + escapeJson(name) + "\",\"repoCount\":" + repoCount + "}", null, sourceIp));
    }

    private String slugify(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class NoSuchTeamException extends RuntimeException {
        public NoSuchTeamException(UUID id) {
            super("No team with id " + id);
        }
    }

    public static class TeamHasDependentsException extends RuntimeException {
        public TeamHasDependentsException(String message) {
            super(message);
        }
    }
}