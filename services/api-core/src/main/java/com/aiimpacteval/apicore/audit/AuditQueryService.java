package com.aiimpacteval.apicore.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Read side of the audit log (ADMIN-only via SecurityConfig). */
@Service
public class AuditQueryService {

    private final JdbcTemplate jdbcTemplate;

    public AuditQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AuditEntry> recent(int limit) {
        return jdbcTemplate.query("""
                        SELECT id, actor_email, action, target_type, target_id, occurred_at
                        FROM core.audit_log
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new AuditEntry(
                        rs.getLong("id"),
                        rs.getString("actor_email"),
                        rs.getString("action"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getTimestamp("occurred_at").toInstant()),
                limit);
    }

    public record AuditEntry(long id, String actorEmail, String action, String targetType,
                             String targetId, Instant occurredAt) {
    }
}
