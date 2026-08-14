# ADR-0002: Queue-isolated connector services; single PostgreSQL with layered schemas for MVP

- **Status:** Accepted
- **Date:** 2026-07-04
- **Deciders:** Vishal, Aditi
- **BRD traceability:** FR-1.8 (resilient ingestion), NFR Availability, Scalability, Extensibility; §11.1/§11.2

## Context

The BRD mandates: connector failures must not affect the core platform or lose data (FR-1.8);
new connectors must not require core re-architecture (NFR Extensibility); dashboards must stay
< 3 s at ~1M events; metric freshness ≤ 15 min. It suggests "dedicated connector microservices
per tool, communicating via a message queue" and "PostgreSQL … plus a columnar/analytical
store at scale."

## Decision

1. **One connector service per external tool**, publishing enveloped raw events to a
   **message queue (RabbitMQ)**. Connectors contain zero business logic. Vendor outages are
   absorbed by backoff + queue buffering; consumer failures go to DLQs with alerts.
2. **Single PostgreSQL 16 instance for MVP with three schemas** acting as the layered stores:
   - `staging` — immutable, append-only raw events (replay/audit source of truth)
   - `core` — normalized entities, identities, teams, RBAC, config, audit log
   - `mart` — materialized, pre-aggregated metric tables the dashboards query
3. The mart is only ever populated by the Metrics Engine and is **fully rebuildable from
   `staging`** — this preserves a clean migration path to ClickHouse/warehouse in Phase 3/4
   without touching ingestion or the API contract.

## Options considered

1. **Chosen: RabbitMQ + single Postgres, layered schemas** — minimal ops burden for a small
   team; retry/DLQ semantics built-in; Postgres comfortably handles MVP event volumes
   (pilot: 2–3 teams).
2. **Kafka + ClickHouse from day one** — matches end-state scale but heavy operational cost
   during MVP; violates "strict phased scope" risk mitigation (BRD §13).
3. **Direct connector→DB writes, no queue** — simplest, but fails FR-1.8: a slow consumer or
   DB contention would back-pressure into webhook handling and risk data loss.

## Consequences

- New connector = new service + new queue binding; core untouched (satisfies Extensibility).
- Must monitor queue depth and ingestion lag (15-min freshness NFR) from day one.
- A future move to Kafka (throughput) or ClickHouse (mart scale) is a swap behind stable
  interfaces — record it as a superseding ADR when triggered by measured load.
- Single-DB MVP means one failure domain; mitigated by managed Postgres + backups; acceptable
  for pilot scale.
