package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.audit.AuditLog;
import com.aiimpacteval.apicore.audit.AuditLog.AuditEvent;
import com.aiimpacteval.apicore.security.AppUser;
import com.aiimpacteval.apicore.security.AppUserRepository;
import com.aiimpacteval.apicore.security.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Write side of user/role administration (PRD E8-S1). This is what makes {@link
 * com.aiimpacteval.apicore.security.DevTokenService}'s login and {@link
 * com.aiimpacteval.apicore.security.ScopeResolver}'s scope enforcement real rather than
 * self-declared: an admin decides who exists, what role they hold, which team pins a MANAGER's
 * scope, and which GitHub identity an IC's Personal Activity view should read. Every write is
 * audited (E8-S3): role/team/identity changes are exactly the kind of access-affecting action
 * the audit trail exists to catch.
 */
@Service
public class AdminUserService {

    private final AppUserRepository appUserRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLog auditLog;

    public AdminUserService(AppUserRepository appUserRepository, JdbcTemplate jdbcTemplate, AuditLog auditLog) {
        this.appUserRepository = appUserRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLog = auditLog;
    }

    public List<AppUserView> listUsers() {
        Map<UUID, String> teamNames = jdbcTemplate.query("SELECT id, name FROM core.team",
                        (rs, rowNum) -> Map.entry((UUID) rs.getObject("id"), rs.getString("name")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return appUserRepository.listAll().stream()
                .map(u -> toView(u, u.teamId() == null ? null : teamNames.get(u.teamId())))
                .toList();
    }

    public AppUserView createUser(String actorEmail, String email, String displayName, Role role,
                                  UUID teamId, String githubLogin, String sourceIp) {
        AppUser created = appUserRepository.create(email, displayName, role, teamId, githubLogin);
        auditLog.write(new AuditEvent(actorEmail, "USER_CREATED", "app_user", created.id().toString(),
                null, toJson(role, teamId), sourceIp));
        return toView(created, teamNameOf(teamId));
    }

    public AppUserView updateRoleAndTeam(String actorEmail, UUID userId, Role role, UUID teamId, String sourceIp) {
        AppUser before = findOrThrow(userId);

        appUserRepository.updateRoleAndTeam(userId, role, teamId);
        auditLog.write(new AuditEvent(actorEmail, "USER_ROLE_CHANGED", "app_user", userId.toString(),
                toJson(before.role(), before.teamId()), toJson(role, teamId), sourceIp));
        return toView(new AppUser(userId, before.email(), before.displayName(), role, teamId,
                before.githubLogin(), before.active(), before.lastLoginAt()), teamNameOf(teamId));
    }

    public AppUserView updateGithubLogin(String actorEmail, UUID userId, String githubLogin, String sourceIp) {
        AppUser before = findOrThrow(userId);

        appUserRepository.updateGithubLogin(userId, githubLogin);
        auditLog.write(new AuditEvent(actorEmail, "USER_GITHUB_LOGIN_CHANGED", "app_user", userId.toString(),
                toJsonString(before.githubLogin()), toJsonString(githubLogin), sourceIp));
        return toView(new AppUser(userId, before.email(), before.displayName(), before.role(),
                before.teamId(), githubLogin, before.active(), before.lastLoginAt()), teamNameOf(before.teamId()));
    }

    public AppUserView setActive(String actorEmail, UUID userId, boolean active, String sourceIp) {
        AppUser before = findOrThrow(userId);

        appUserRepository.updateActive(userId, active);
        auditLog.write(new AuditEvent(actorEmail, active ? "USER_REACTIVATED" : "USER_DEACTIVATED",
                "app_user", userId.toString(), null, null, sourceIp));
        return toView(new AppUser(userId, before.email(), before.displayName(), before.role(),
                before.teamId(), before.githubLogin(), active, before.lastLoginAt()), teamNameOf(before.teamId()));
    }

    private AppUser findOrThrow(UUID userId) {
        return appUserRepository.listAll().stream()
                .filter(u -> u.id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new NoSuchAdminUserException(userId));
    }

    private String teamNameOf(UUID teamId) {
        if (teamId == null) {
            return null;
        }
        return jdbcTemplate.query("SELECT name FROM core.team WHERE id = ?",
                        (rs, rowNum) -> rs.getString("name"), teamId)
                .stream().findFirst().orElse(null);
    }

    private static String toJson(Role role, UUID teamId) {
        return "{\"role\":\"" + role.name() + "\",\"teamId\":"
                + (teamId == null ? "null" : "\"" + teamId + "\"") + "}";
    }

    /**
     * {@code audit_log.before_state}/{@code after_state} are {@code jsonb} columns — a bare
     * value like {@code 0pain01} isn't valid JSON on its own (Postgres wants a quoted JSON
     * string), which is exactly what made {@link #updateGithubLogin} 500 rather than record the
     * change. Every value written to those columns needs to go through something like this, not
     * be passed through raw.
     */
    private static String toJsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private static AppUserView toView(AppUser u, String teamName) {
        return new AppUserView(u.id(), u.email(), u.displayName(), u.role().name(),
                u.teamId(), teamName, u.githubLogin(), u.active(), u.lastLoginAt());
    }

    public record AppUserView(UUID id, String email, String displayName, String role,
                              UUID teamId, String teamName, String githubLogin,
                              boolean active, Instant lastLoginAt) {
    }

    public static class NoSuchAdminUserException extends RuntimeException {
        public NoSuchAdminUserException(UUID id) {
            super("No app_user with id " + id);
        }
    }
}