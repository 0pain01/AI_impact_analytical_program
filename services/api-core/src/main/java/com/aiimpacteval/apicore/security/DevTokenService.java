package com.aiimpacteval.apicore.security;

import com.aiimpacteval.apicore.audit.AuditLog;
import com.aiimpacteval.apicore.audit.AuditLog.AuditEvent;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues short-lived JWTs for the dev/pilot auth bridge (ADR-0004). Not the production auth
 * path — that's SSO/OIDC.
 *
 * <p>Previously this trusted a client-supplied {@code role} query param outright — anyone could
 * request {@code role=ADMIN} for any email with no account behind it (the actual mechanism
 * behind the gap CockpitController's javadoc flagged: "any analytical-role caller can pass any
 * scope"). Role and team scope now come from {@code core.app_user} instead:
 * <ul>
 * <li>a known, active email gets the role/team on file — the client can no longer request a
 * role or scope at all;</li>
 * <li>the very first login ever on a fresh install bootstraps that email as ADMIN (someone has
 * to be able to add the rest of the team from the Admin console);</li>
 * <li>anyone else gets rejected with {@link NoSuchAppUserException}.</li>
 * </ul>
 */
@Service
public class DevTokenService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final String ISSUER = "aiimpacteval-dev";

    private final JwtEncoder jwtEncoder;
    private final AuditLog auditLog;
    private final AppUserRepository appUserRepository;

    public DevTokenService(JwtEncoder jwtEncoder, AuditLog auditLog, AppUserRepository appUserRepository) {
        this.jwtEncoder = jwtEncoder;
        this.auditLog = auditLog;
        this.appUserRepository = appUserRepository;
    }

    public IssuedToken issue(String email, String sourceIp) {
        AppUser user = appUserRepository.findByEmail(email)
                .filter(AppUser::active)
                .orElseGet(() -> bootstrapIfFirstEver(email, sourceIp));

        appUserRepository.touchLastLogin(user.id());

        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.email())
                .claim("role", user.role().name())
                .claim("scope", user.teamId() == null ? "*" : user.teamId().toString())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        auditLog.write(new AuditEvent(user.email(), "AUTH_TOKEN_ISSUED", "auth", user.role().name(),
                null, null, sourceIp));
        return new IssuedToken(token, user.role().name(), expiresAt);
    }

    private AppUser bootstrapIfFirstEver(String email, String sourceIp) {
        if (appUserRepository.count() > 0) {
            throw new NoSuchAppUserException(email);
        }
        AppUser bootstrapped = appUserRepository.create(email, email, Role.ADMIN, null, null);
        auditLog.write(AuditEvent.of(email, "ADMIN_BOOTSTRAPPED", "app_user", bootstrapped.id().toString()));
        return bootstrapped;
    }

    public record IssuedToken(String token, String role, Instant expiresAt) {
    }
}