package com.aiimpacteval.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiimpacteval.common.events.EventEnvelope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requires Docker (Testcontainers); skipped when the Docker API is not reachable — e.g.
 * Docker Desktop with Enhanced Container Isolation blocks non-CLI socket clients (returns
 * HTTP 400 stubs). The same idempotency path is covered end-to-end by infra/smoke-e2e.sh,
 * which drives real Postgres via the docker CLI.
 */
@Testcontainers(disabledWithoutDocker = true)
class StagingEventWriterIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JdbcTemplate jdbc;
    private StagingEventWriter writer;

    @BeforeAll
    static void createSchema() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        // Mirrors api-core's V1 migration for staging.raw_event — the constraint name is
        // load-bearing (the writer's ON CONFLICT references it).
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS staging");
        jdbc.execute("""
                CREATE TABLE staging.raw_event (
                    id                BIGSERIAL PRIMARY KEY,
                    source            TEXT        NOT NULL,
                    source_id         TEXT        NOT NULL,
                    event_type        TEXT        NOT NULL,
                    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
                    connector_version TEXT        NOT NULL,
                    payload           JSONB       NOT NULL,
                    CONSTRAINT uq_raw_event_natural_key UNIQUE (source, source_id, event_type)
                )
                """);
        // Mirrors api-core's V11 migration — write() unconditionally upserts this on every
        // event, duplicates included, so it must exist even for tests that only exercise
        // raw_event.
        jdbc.execute("""
                CREATE TABLE staging.connector_activity (
                    source           TEXT PRIMARY KEY,
                    last_checked_at  TIMESTAMPTZ NOT NULL
                )
                """);
        // Mirrors api-core's V12 migration — needed since the constructor now always requires a
        // seat-cost value, even though these tests only exercise the raw_event/github path.
        jdbc.execute("""
                CREATE TABLE staging.ai_usage_state (
                    source                TEXT NOT NULL,
                    actor_key             TEXT NOT NULL,
                    day                   DATE NOT NULL,
                    sessions              INT,
                    loc_added             INT,
                    loc_removed           INT,
                    commits               INT,
                    prs                   INT,
                    cost_usd              NUMERIC,
                    tokens_input          BIGINT,
                    tokens_output         BIGINT,
                    prompts               INT,
                    requests              INT,
                    accepted_suggestions  INT,
                    rejected_suggestions  INT,
                    primary_surface       TEXT,
                    last_received_at      TIMESTAMPTZ NOT NULL,
                    PRIMARY KEY (source, actor_key, day)
                )
                """);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM staging.raw_event");
        jdbc.update("DELETE FROM staging.connector_activity");
        writer = new StagingEventWriter(jdbc, MAPPER, BigDecimal.valueOf(19));
    }

    @Test
    void writesEventAndSkipsRedelivery() {
        EventEnvelope envelope = envelope("delivery-1", "pull_request");

        assertTrue(writer.write(envelope), "first delivery should insert");
        assertFalse(writer.write(envelope), "redelivery should be an idempotent no-op");

        assertEquals(1, countRows());
        String storedPayload = jdbc.queryForObject(
                "SELECT payload->>'action' FROM staging.raw_event", String.class);
        assertEquals("opened", storedPayload);
    }

    @Test
    void distinctEventTypesForSameSourceIdAreSeparateRows() {
        assertTrue(writer.write(envelope("delivery-2", "pull_request")));
        assertTrue(writer.write(envelope("delivery-2", "push")));
        assertEquals(2, countRows());
    }

    private static EventEnvelope envelope(String sourceId, String eventType) {
        var payload = MAPPER.createObjectNode().put("action", "opened").put("number", 42);
        return new EventEnvelope("github", sourceId, eventType,
                Instant.parse("2026-07-04T12:00:00Z"), "0.1.0", payload);
    }

    private static int countRows() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM staging.raw_event", Integer.class);
        return count == null ? 0 : count;
    }
}
