package com.aiimpacteval.apicore.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for reading/writing {@code core.app_user}. JDBC impl ({@link JdbcAppUserRepository}) in
 * production, a fake in tests — same split as {@link com.aiimpacteval.apicore.audit.AuditLog} /
 * {@code JdbcAuditLog}. Shared by {@link DevTokenService} (login: look a user up, or bootstrap
 * the first one), {@code admin.AdminUserService} (the Admin console's user management panel),
 * and {@code personal.PersonalQueryService} (resolving a caller's GitHub identity).
 */
public interface AppUserRepository {

    Optional<AppUser> findByEmail(String email);

    List<AppUser> listAll();

    long count();

    AppUser create(String email, String displayName, Role role, UUID teamId, String githubLogin);

    void updateRoleAndTeam(UUID id, Role role, UUID teamId);

    void updateGithubLogin(UUID id, String githubLogin);

    void updateActive(UUID id, boolean active);

    void touchLastLogin(UUID id);
}