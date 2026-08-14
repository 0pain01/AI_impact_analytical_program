package com.aiimpacteval.apicore.security;

import com.aiimpacteval.apicore.audit.AuditLog;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Issued tokens must decode, carry the role claim, expire in 15 min, and be audited. */
class DevTokenServiceTest {

    static class RecordingAuditLog implements AuditLog {
        final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void write(AuditEvent event) {
            events.add(event);
        }
    }

    @Test
    void issuesDecodableAuditedTokenWithRoleClaim() {
        var jwtConfig = new JwtConfig();
        var rsaKey = jwtConfig.rsaKey();
        var encoder = jwtConfig.jwtEncoder(rsaKey);
        JwtDecoder decoder = jwtConfig.jwtDecoder(rsaKey);
        var audit = new RecordingAuditLog();
        var service = new DevTokenService(encoder, audit);

        var issued = service.issue("lead@example.com", Role.ENG_LEADER, "127.0.0.1");

        Jwt decoded = decoder.decode(issued.token());
        assertEquals("lead@example.com", decoded.getSubject());
        assertEquals("ENG_LEADER", decoded.getClaimAsString("role"));
        assertEquals(issued.expiresAt().getEpochSecond(),
                java.util.Objects.requireNonNull(decoded.getExpiresAt()).getEpochSecond());

        assertEquals(1, audit.events.size());
        var event = audit.events.get(0);
        assertEquals("AUTH_TOKEN_ISSUED", event.action());
        assertEquals("lead@example.com", event.actorEmail());
        assertEquals("ENG_LEADER", event.targetId());
        assertTrue(DevTokenService.TOKEN_TTL.toMinutes() == 15);
    }
}
