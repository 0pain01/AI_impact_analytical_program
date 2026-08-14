package com.aiimpacteval.apicore.audit;

/**
 * Append-only audit trail port (E8-S3, NFR Auditability). JDBC impl in production, recording
 * fake in tests. Callers never update or delete — the log is write-once.
 */
public interface AuditLog {

    void write(AuditEvent event);

    record AuditEvent(String actorEmail, String action, String targetType, String targetId,
                      String beforeJson, String afterJson, String sourceIp) {

        public static AuditEvent of(String actorEmail, String action, String targetType, String targetId) {
            return new AuditEvent(actorEmail, action, targetType, targetId, null, null, null);
        }
    }
}
