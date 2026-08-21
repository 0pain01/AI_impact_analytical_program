package com.aiimpacteval.connector.aitelemetry.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.connector.aitelemetry.events.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

/**
 * Backfills Claude Code usage/cost (PRD E9, AI-01/AI-02/AI-03) by reading a usage report file
 * shaped exactly like Anthropic's real Admin API usage-report response ({@code {"data": [{date,
 * actor, organization_id, core_metrics, tool_actions, model_breakdown}, ...]}}) — verified
 * against a real sample export, not assumed. {@link #backfill()} is the seam a future real
 * integration replaces: swap the file read in {@link #readReport()} for an authenticated call to
 * Anthropic's usage-report endpoint and everything downstream (event shape, staging projection,
 * AI Cost Track API) needs zero changes, since the payload this publishes IS that API's response
 * shape verbatim.
 *
 * <p>One event per {@code (actor.email_address, date)} record — that pair is the file's natural
 * grain and this connector's idempotency key (ADR-0003), matching how a real daily usage report
 * would re-deliver the same day's row unchanged on every refresh.
 */
@Service
public class ClaudeCodeUsageBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeUsageBackfillService.class);

    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String usageFilePath;

    public ClaudeCodeUsageBackfillService(EventPublisher publisher, ObjectMapper objectMapper, Clock clock,
                                          @Value("${ai-telemetry.claude-code.usage-file-path}") String usageFilePath) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.usageFilePath = usageFilePath;
    }

    public BackfillResult backfill() {
        JsonNode report = readReport();
        JsonNode records = report == null ? null : report.get("data");
        int published = 0;
        if (records != null && records.isArray()) {
            for (JsonNode record : records) {
                String email = textAtPath(record, "actor", "email_address");
                String date = textOrNull(record, "date");
                if (email == null || date == null) {
                    continue;
                }
                String day = date.length() >= 10 ? date.substring(0, 10) : date;
                publisher.publish(new EventEnvelope(
                        "claude_code",
                        "claude_code:" + email + ":" + day,
                        "usage.snapshot",
                        Instant.now(clock),
                        CONNECTOR_VERSION,
                        record));
                published++;
            }
        }
        log.info("Claude Code usage backfill complete: {} daily user records", published);
        return new BackfillResult(published);
    }

    /**
     * Reads the configured usage-report file. This is the ONLY method a real Anthropic Admin API
     * integration needs to replace — everything else in this class operates purely on the
     * resulting {@link JsonNode}, agnostic to whether it came from disk or the wire.
     */
    private JsonNode readReport() {
        if (usageFilePath == null || usageFilePath.isBlank()) {
            throw new BackfillException(
                    "ai-telemetry.claude-code.usage-file-path is not configured — set CLAUDE_CODE_USAGE_FILE", null);
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(usageFilePath));
            return objectMapper.readTree(bytes);
        } catch (IOException e) {
            throw new BackfillException("Could not read Claude Code usage file at " + usageFilePath, e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String textAtPath(JsonNode node, String... path) {
        JsonNode cur = node;
        for (String p : path) {
            if (cur == null || cur.isNull()) {
                return null;
            }
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() ? null : cur.asText();
    }

    public record BackfillResult(int records) {
    }

    public static class BackfillException extends RuntimeException {
        public BackfillException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
