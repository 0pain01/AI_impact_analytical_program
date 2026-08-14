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
 * Issues short-lived role-scoped JWTs for the dev/pilot auth bridge (ADR-0004). Every issuance
 * is audited as an access grant (E8-S3). Not the production auth path.
 */
@Service
public class DevTokenService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final String ISSUER = "aiimpacteval-dev";

    private final JwtEncoder jwtEncoder;
    private final AuditLog auditLog;

    public DevTokenService(JwtEncoder jwtEncoder, AuditLog auditLog) {
        this.jwtEncoder = jwtEncoder;
        this.auditLog = auditLog;
    }

    public IssuedToken issue(String email, Role role, String sourceIp) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(TOKEN_TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(email)
                .claim("role", role.name())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        auditLog.write(new AuditEvent(email, "AUTH_TOKEN_ISSUED", "auth", role.name(),
                null, null, sourceIp));
        return new IssuedToken(token, role.name(), expiresAt);
    }

    public record IssuedToken(String token, String role, Instant expiresAt) {
    }
}
