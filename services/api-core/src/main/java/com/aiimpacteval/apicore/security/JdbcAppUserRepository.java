package com.aiimpacteval.apicore.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC-backed {@link AppUserRepository} — the production implementation. */
@Repository
public class JdbcAppUserRepository implements AppUserRepository {

    private static final RowMapper<AppUser> ROW_MAPPER = (rs, rowNum) -> new AppUser(
            (UUID) rs.getObject("id"),
            rs.getString("email"),
            rs.getString("display_name"),
            Role.fromString(rs.getString("role")),
            (UUID) rs.getObject("team_id"),
            rs.getString("github_login"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("last_login_at") == null ? null : rs.getTimestamp("last_login_at").toInstant());

    private static final String SELECT_COLUMNS =
            "id, email, display_name, role, team_id, github_login, is_active, last_login_at ";

    private final JdbcTemplate jdbcTemplate;

    public JdbcAppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        List<AppUser> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM core.app_user WHERE lower(email) = lower(?)",
                ROW_MAPPER, email);
        return rows.stream().findFirst();
    }

    @Override
    public List<AppUser> listAll() {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + "FROM core.app_user ORDER BY email",
                ROW_MAPPER);
    }

    @Override
    public long count() {
        Long n = jdbcTemplate.queryForObject("SELECT count(*) FROM core.app_user", Long.class);
        return n == null ? 0 : n;
    }

    @Override
    public AppUser create(String email, String displayName, Role role, UUID teamId, String githubLogin) {
        jdbcTemplate.queryForObject("""
                INSERT INTO core.app_user (email, display_name, role, team_id, github_login, is_active)
                VALUES (?, ?, ?, ?, ?, TRUE)
                RETURNING id
                """, UUID.class, email, displayName, role.name(), teamId, githubLogin);
        return findByEmail(email).orElseThrow(() ->
                new IllegalStateException("Just-inserted app_user " + email + " vanished"));
    }

    @Override
    public void updateRoleAndTeam(UUID id, Role role, UUID teamId) {
        jdbcTemplate.update(
                "UPDATE core.app_user SET role = ?, team_id = ? WHERE id = ?",
                role.name(), teamId, id);
    }

    @Override
    public void updateGithubLogin(UUID id, String githubLogin) {
        jdbcTemplate.update("UPDATE core.app_user SET github_login = ? WHERE id = ?", githubLogin, id);
    }

    @Override
    public void updateActive(UUID id, boolean active) {
        jdbcTemplate.update("UPDATE core.app_user SET is_active = ? WHERE id = ?", active, id);
    }

    @Override
    public void touchLastLogin(UUID id) {
        jdbcTemplate.update(
                "UPDATE core.app_user SET last_login_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), id);
    }
}