package com.aiimpacteval.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import com.aiimpacteval.common.events.EventTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

/**
 * Persists every connector event into the immutable staging store.
 *
 * <p>Idempotent by the {@code (source, source_id, event_type)} natural key (ADR-0003):
 * redeliveries and replays are no-ops. Unrecoverable messages are rejected without requeue,
 * which routes them to the DLQ — never silently dropped (FR-1.8).
 *
 * <p>Also maintains {@code staging.workflow_run_state} / {@code staging.pull_request_state} —
 * typed, indexed "latest known state" projections of {@code workflow_run.snapshot} /
 * {@code pull_request.snapshot} events. {@code staging.raw_event} stays the source of truth;
 * these projections exist purely so metrics-engine doesn't have to re-derive "latest state per
 * run/PR" from JSONB via DISTINCT ON on every recompute (see V5 migration for the one-time
 * backfill of pre-existing events, and the perf history that motivated this).
 */
@Component
public class StagingEventWriter {

    private static final Logger log = LoggerFactory.getLogger(StagingEventWriter.class);

    private static final String INSERT_SQL = """
            INSERT INTO staging.raw_event (source, source_id, event_type, received_at, connector_version, payload)
            VALUES (?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT ON CONSTRAINT uq_raw_event_natural_key DO NOTHING
            """;

    // last_received_at guard: if events ever arrive out of order (retry/requeue), an older
    // snapshot can never clobber a newer one that already landed — matches the "latest by
    // received_at wins" semantics the old DISTINCT ON ... ORDER BY received_at DESC query used.
    private static final String UPSERT_WORKFLOW_RUN_SQL = """
            INSERT INTO staging.workflow_run_state (repo, run_id, conclusion, name, ts, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, run_id) DO UPDATE SET
                conclusion = EXCLUDED.conclusion, name = EXCLUDED.name, ts = EXCLUDED.ts,
                last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.workflow_run_state.last_received_at
            """;

    private static final String UPSERT_PULL_REQUEST_SQL = """
            INSERT INTO staging.pull_request_state (repo, pr_id, created_at, merged_at, last_received_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (repo, pr_id) DO UPDATE SET
                created_at = EXCLUDED.created_at, merged_at = EXCLUDED.merged_at,
                last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.pull_request_state.last_received_at
            """;

    // Reviews can be dismissed after submission (state changes to DISMISSED), so this is a
    // guarded upsert like the others, not an insert-once.
    private static final String UPSERT_PULL_REQUEST_REVIEW_SQL = """
            INSERT INTO staging.pull_request_review_state
                (repo, pr_number, review_id, reviewer_login, state, submitted_at, last_received_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (repo, review_id) DO UPDATE SET
                reviewer_login = EXCLUDED.reviewer_login, state = EXCLUDED.state,
                submitted_at = EXCLUDED.submitted_at, last_received_at = EXCLUDED.last_received_at
            WHERE EXCLUDED.last_received_at > staging.pull_request_review_state.last_received_at
            """;

    private static final Set<String> WORKFLOW_RUN_EVENT_TYPES = Set.of("workflow_run", "workflow_run.snapshot");
    private static final Set<String> PULL_REQUEST_EVENT_TYPES = Set.of("pull_request", "pull_request.snapshot");
    private static final Set<String> PULL_REQUEST_REVIEW_EVENT_TYPES =
            Set.of("pull_request_review", "pull_request_review.snapshot");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public StagingEventWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = EventTopology.STAGING_QUEUE)
    public void onEvent(EventEnvelope envelope) {
        write(envelope);
    }

    /** @return true if a new row was written, false if it was a duplicate (idempotent skip). */
    public boolean write(EventEnvelope envelope) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope.payload());
        } catch (JsonProcessingException e) {
            // Malformed beyond repair — reject without requeue so it lands in the DLQ.
            throw new AmqpRejectAndDontRequeueException("Unserializable payload for " + envelope.sourceId(), e);
        }

        int inserted = jdbcTemplate.update(INSERT_SQL,
                envelope.source(),
                envelope.sourceId(),
                envelope.eventType(),
                Timestamp.from(envelope.receivedAt()),
                envelope.connectorVersion(),
                payloadJson);

        if (inserted == 0) {
            log.debug("Duplicate event skipped: {}/{}/{}",
                    envelope.source(), envelope.sourceId(), envelope.eventType());
            return false;
        }

        if ("github".equals(envelope.source())) {
            if (WORKFLOW_RUN_EVENT_TYPES.contains(envelope.eventType())) {
                upsertWorkflowRunState(envelope);
            } else if (PULL_REQUEST_EVENT_TYPES.contains(envelope.eventType())) {
                upsertPullRequestState(envelope);
            } else if (PULL_REQUEST_REVIEW_EVENT_TYPES.contains(envelope.eventType())) {
                upsertPullRequestReviewState(envelope);
            }
        }
        return true;
    }

    private void upsertWorkflowRunState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode run = payload.has("workflow_run") ? payload.get("workflow_run") : payload;

        String runId = textOrNull(run, "id");
        if (runId == null) {
            return; // nothing to key the projection on — skip rather than guess
        }
        String repo = firstNonBlank(
                textAtPath(run, "repository", "full_name"),
                textAtPath(payload, "repository", "full_name"),
                "unknown");
        String conclusion = textOrNull(run, "conclusion");
        String name = textOrNull(run, "name");
        Instant ts = instantOrNull(textOrNull(run, "updated_at"));

        jdbcTemplate.update(UPSERT_WORKFLOW_RUN_SQL,
                repo, runId, conclusion, name,
                ts == null ? null : Timestamp.from(ts),
                Timestamp.from(envelope.receivedAt()));
    }

    private void upsertPullRequestState(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        JsonNode pr = payload.has("pull_request") ? payload.get("pull_request") : payload;

        String prId = textOrNull(pr, "id");
        if (prId == null) {
            return;
        }
        String repo = firstNonBlank(textAtPath(pr, "base", "repo", "full_name"), "unknown");
        Instant createdAt = instantOrNull(textOrNull(pr, "created_at"));
        Instant mergedAt = instantOrNull(textOrNull(pr, "merged_at"));

        jdbcTemplate.update(UPSERT_PULL_REQUEST_SQL,
                repo, prId,
                createdAt == null ? null : Timestamp.from(createdAt),
                mergedAt == null ? null : Timestamp.from(mergedAt),
                Timestamp.from(envelope.receivedAt()));
    }

    private void upsertPullRequestReviewState(EventEnvelope envelope) {
        JsonNode review = envelope.payload();

        String reviewId = textOrNull(review, "id");
        String prUrl = textOrNull(review, "pull_request_url");
        if (reviewId == null || prUrl == null) {
            return;
        }
        RepoAndPrNumber location = parsePullRequestUrl(prUrl);
        if (location == null) {
            log.warn("Could not parse repo/PR number from pull_request_url '{}' — skipping review {}",
                    prUrl, reviewId);
            return;
        }
        String reviewerLogin = textAtPath(review, "user", "login");
        String state = textOrNull(review, "state");
        Instant submittedAt = instantOrNull(textOrNull(review, "submitted_at"));

        jdbcTemplate.update(UPSERT_PULL_REQUEST_REVIEW_SQL,
                location.repo(), location.prNumber(), reviewId, reviewerLogin, state,
                submittedAt == null ? null : Timestamp.from(submittedAt),
                Timestamp.from(envelope.receivedAt()));
    }

    private record RepoAndPrNumber(String repo, long prNumber) {
    }

    // e.g. "https://api.github.com/repos/expressjs/express/pulls/7369" -> ("expressjs/express", 7369)
    private static RepoAndPrNumber parsePullRequestUrl(String url) {
        int reposIdx = url.indexOf("/repos/");
        int pullsIdx = url.indexOf("/pulls/");
        if (reposIdx < 0 || pullsIdx < 0 || pullsIdx <= reposIdx) {
            return null;
        }
        String repo = url.substring(reposIdx + "/repos/".length(), pullsIdx);
        String numberStr = url.substring(pullsIdx + "/pulls/".length());
        try {
            return new RepoAndPrNumber(repo, Long.parseLong(numberStr));
        } catch (NumberFormatException e) {
            return null;
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

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static Instant instantOrNull(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            log.warn("Unparseable timestamp '{}' — leaving null", iso);
            return null;
        }
    }
}