package com.aiimpacteval.apicore.security;

import com.aiimpacteval.apicore.audit.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issued tokens must decode, carry the role/scope claims from {@code core.app_user} (not from
 * the caller), expire in 15 min, and be audited. Also covers the two account-resolution paths:
 * an existing active user, and the first-ever-login bootstrap.
 */
class DevTokenServiceTest {

    static class RecordingAuditLog implements AuditLog {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void write(AuditEvent event) {
            events.add(event);
        }
    }

    /** In-memory {@link AppUserRepository} — same role a hand-rolled fake plays for AuditLog elsewhere. */
    static class FakeAppUserRepository implements AppUserRepository {
        final Map<UUID, AppUser> byId = new HashMap<>();

        @Override
        public Optional<AppUser> findByEmail(String email) {
            return byId.values().stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public List<AppUser> listAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public long count() {
            return byId.size();
        }

        @Override
        public AppUser create(String email, String displayName, Role role, UUID teamId, String githubLogin) {
            AppUser user = new AppUser(UUID.randomUUID(), email, displayName, role, teamId, githubLogin, true, null);
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public void updateRoleAndTeam(UUID id, Role role, UUID teamId) {
            AppUser u = byId.get(id);
            byId.put(id, new AppUser(u.id(), u.email(), u.displayName(), role, teamId, u.githubLogin(), u.active(), u.lastLoginAt()));
        }

        @Override
        public void updateGithubLogin(UUID id, String githubLogin) {
            AppUser u = byId.get(id);
            byId.put(id, new AppUser(u.id(), u.email(), u.displayName(), u.role(), u.teamId(), githubLogin, u.active(), u.lastLoginAt()));
        }

        @Override
        public void updateActive(UUID id, boolean active) {
            AppUser u = byId.get(id);
            byId.put(id, new AppUser(u.id(), u.email(), u.displayName(), u.role(), u.teamId(), u.githubLogin(), active, u.lastLoginAt()));
        }

        @Override
        public void touchLastLogin(UUID id) {
            AppUser u = byId.get(id);
            byId.put(id, new AppUser(u.id(), u.email(), u.displayName(), u.role(), u.teamId(), u.githubLogin(), u.active(), Instant.now()));
        }
    }

    private DevTokenService newService(FakeAppUserRepository repo, RecordingAuditLog audit) {
        var jwtConfig = new JwtConfig();
        var rsaKey = jwtConfig.rsaKey();
        var encoder = jwtConfig.jwtEncoder(rsaKey);
        return new DevTokenService(encoder, audit, repo);
    }

    @Test
    void issuesDecodableAuditedTokenWithRoleAndScopeClaimsForAnExistingUser() {
        var jwtConfig = new JwtConfig();
        var rsaKey = jwtConfig.rsaKey();
        JwtDecoder decoder = jwtConfig.jwtDecoder(rsaKey);
        var audit = new RecordingAuditLog();
        var repo = new FakeAppUserRepository();
        UUID teamId = UUID.randomUUID();
        repo.create("lead@example.com", "Lead", Role.MANAGER, teamId, null);

        var service = new DevTokenService(jwtConfig.jwtEncoder(rsaKey), audit, repo);
        var issued = service.issue("lead@example.com", "127.0.0.1");

        Jwt decoded = decoder.decode(issued.token());
        assertEquals("lead@example.com", decoded.getSubject());
        assertEquals("MANAGER", decoded.getClaimAsString("role"));
        assertEquals(teamId.toString(), decoded.getClaimAsString("scope"));
        assertEquals(issued.expiresAt().getEpochSecond(),
                java.util.Objects.requireNonNull(decoded.getExpiresAt()).getEpochSecond());

        assertEquals(1, audit.events.size());
        var event = audit.events.get(0);
        assertEquals("AUTH_TOKEN_ISSUED", event.action());
        assertEquals("lead@example.com", event.actorEmail());
        assertEquals("MANAGER", event.targetId());
        assertTrue(DevTokenService.TOKEN_TTL.toMinutes() == 15);
    }

    @Test
    void firstEverLoginBootstrapsAsAdmin() {
        var audit = new RecordingAuditLog();
        var repo = new FakeAppUserRepository();
        var service = newService(repo, audit);

        var issued = service.issue("first@example.com", "127.0.0.1");

        assertEquals("ADMIN", issued.role());
        assertEquals(1, repo.count());
        assertTrue(audit.events.stream().anyMatch(e -> "ADMIN_BOOTSTRAPPED".equals(e.action())));
    }

    @Test
    void unknownEmailIsRejectedOnceAnAccountAlreadyExists() {
        var audit = new RecordingAuditLog();
        var repo = new FakeAppUserRepository();
        repo.create("someone@example.com", "Someone", Role.IC, null, null);
        var service = newService(repo, audit);

        assertThrows(NoSuchAppUserException.class, () -> service.issue("stranger@example.com", "127.0.0.1"));
    }

    @Test
    void deactivatedUserIsRejectedEvenThoughARowExists() {
        var audit = new RecordingAuditLog();
        var repo = new FakeAppUserRepository();
        AppUser user = repo.create("gone@example.com", "Gone", Role.IC, null, null);
        repo.updateActive(user.id(), false);
        var service = newService(repo, audit);

        assertThrows(NoSuchAppUserException.class, () -> service.issue("gone@example.com", "127.0.0.1"));
    }
}