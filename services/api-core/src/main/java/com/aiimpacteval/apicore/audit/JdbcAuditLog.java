package com.aiimpacteval.apicore.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcAuditLog implements AuditLog {

    private static final String INSERT = """
            INSERT INTO core.audit_log
                (actor_email, action, target_type, target_id, before_state, after_state, source_ip)
            VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditLog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(AuditEvent event) {
        jdbcTemplate.update(INSERT,
                event.actorEmail(), event.action(), event.targetType(), event.targetId(),
                event.beforeJson(), event.afterJson(), event.sourceIp());
    }
}
