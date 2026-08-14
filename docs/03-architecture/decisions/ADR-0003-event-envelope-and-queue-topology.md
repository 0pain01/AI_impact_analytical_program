# ADR-0003: Event envelope contract and RabbitMQ topology

- **Status:** Accepted
- **Date:** 2026-07-04
- **Deciders:** Vishal, Aditi
- **BRD traceability:** FR-1.8 (resilient ingestion, no data loss), NFR Extensibility, ADR-0002

## Context

Connectors publish raw vendor events; downstream consumers (staging writer, identity service,
metrics engine) must consume them without coupling to any vendor schema. We need a stable
envelope contract and a queue topology that lets us add connectors and consumers independently.

## Decision

### Envelope (JSON, shared `platform-common` module)

```json
{
  "source": "github",
  "sourceId": "<idempotency key, see below>",
  "eventType": "pull_request",
  "receivedAt": "2026-07-04T12:00:00Z",
  "connectorVersion": "0.1.0",
  "payload": { "...raw vendor payload, unmodified..." }
}
```

- `(source, sourceId, eventType)` is the **idempotency key**, matching the
  `staging.raw_event` unique constraint. Redeliveries and replays are no-ops.
- **Webhook events:** `sourceId` = vendor delivery ID (e.g., GitHub `X-GitHub-Delivery` GUID).
- **Backfill events:** `sourceId` = `{entityType}:{entityId}:{updatedAt}` — re-running a
  backfill skips unchanged entities but re-ingests updated ones as new immutable rows
  (staging is append-only; downstream layers pick the latest version per entity).
- `payload` is the raw vendor response/webhook body, untouched — connectors own no business
  logic (ADR-0002); all interpretation happens downstream and stays reprocessable.

### RabbitMQ topology

- Topic exchange **`aiimpacteval.events`** (durable). Routing key: **`{source}.{eventType}`**
  (e.g., `github.pull_request`).
- Queue **`staging.events`** bound with `#` — the ingestion writer persists *every* event.
- Future consumers (identity, metrics) declare their own queues with selective bindings —
  adding one never affects existing flows (NFR Extensibility).
- Dead-lettering: `staging.events` declares DLX **`aiimpacteval.events.dlx`** → queue
  **`staging.events.dlq`**. Poison messages are quarantined, never dropped; DLQ depth is an
  alerting signal (engineering standards §7).
- Publishes and queues are durable/persistent; consumers ack manually after successful write.

## Options considered

1. **Chosen: raw payload passthrough + thin envelope** — connectors stay dumb, everything
   reprocessable from staging.
2. **Normalize in connectors** — smaller staged data but pushes business logic into
   connectors, violating ADR-0002 and making schema evolution a connector redeploy.
3. **One queue per connector, direct exchange** — simpler binding but every new consumer
   requires touching all connector configs; fails extensibility.

## Consequences

- Staging stores full vendor payloads → higher storage; acceptable (metadata-scale, and we
  deliberately exclude file contents in what we request from vendor APIs).
- Backfill `sourceId` scheme means an entity updated N times appears N times in staging —
  by design; downstream dedupes by entity + recency.
- Envelope changes are breaking for all consumers → version bumps via `connectorVersion` and
  additive-only evolution.
