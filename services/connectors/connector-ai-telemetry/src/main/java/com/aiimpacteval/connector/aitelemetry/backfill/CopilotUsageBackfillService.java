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
import java.util.List;

/**
 * Backfills GitHub Copilot usage (PRD E9, AI-01/AI-02/AI-03) by reading a newline-delimited JSON
 * usage export shaped exactly like GitHub's real Copilot Metrics/enterprise usage export — one
 * JSON object per line, per {@code (user_login, day)} — verified against a real sample export,
 * not assumed. Same seam as {@link ClaudeCodeUsageBackfillService}: {@link #readReportLines()}
 * is the only method a real GitHub API integration needs to replace.
 *
 * <p>Unlike Claude Code's usage report, Copilot's export carries no per-request dollar cost —
 * it's a flat-fee per-seat product. {@code StagingEventWriter} applies a configurable per-seat
 * monthly cost to active days only (AI-01's documented edge case: "un-metered/flat-fee tools use
 * allocated seat cost"), not a blind flat charge on every calendar day.
 */
@Service
public class CopilotUsageBackfillService {

    static final String CONNECTOR_VERSION = "0.1.0";
    private static final Logger log = LoggerFactory.getLogger(CopilotUsageBackfillService.class);

    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String usageFilePath;

    public CopilotUsageBackfillService(EventPublisher publisher, ObjectMapper objectMapper, Clock clock,
                                       @Value("${ai-telemetry.copilot.usage-file-path}") String usageFilePath) {
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.usageFilePath = usageFilePath;
    }

    public BackfillResult backfill() {
        List<String> lines = readReportLines();
        int published = 0;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            JsonNode record;
            try {
                record = objectMapper.readTree(line);
            } catch (IOException e) {
                log.warn("Skipping unparseable Copilot usage line: {}", e.getMessage());
                continue;
            }
            String login = textOrNull(record, "user_login");
            String day = textOrNull(record, "day");
            if (login == null || day == null) {
                continue;
            }
            publisher.publish(new EventEnvelope(
                    "copilot",
                    "copilot:" + login + ":" + day,
                    "usage.snapshot",
                    Instant.now(clock),
                    CONNECTOR_VERSION,
                    record));
            published++;
        }
        log.info("Copilot usage backfill complete: {} daily user records", published);
        return new BackfillResult(published);
    }

    /** The only method a real GitHub Copilot Metrics API integration needs to replace. */
    private List<String> readReportLines() {
        if (usageFilePath == null || usageFilePath.isBlank()) {
            throw new BackfillException(
                    "ai-telemetry.copilot.usage-file-path is not configured — set COPILOT_USAGE_FILE", null);
        }
        try {
            return Files.readAllLines(Path.of(usageFilePath));
        } catch (IOException e) {
            throw new BackfillException("Could not read Copilot usage file at " + usageFilePath, e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    public record BackfillResult(int records) {
    }

    public static class BackfillException extends RuntimeException {
        public BackfillException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
