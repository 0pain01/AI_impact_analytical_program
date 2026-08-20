# Changelog

One line per user-visible or architecturally significant change. Newest first.

## 2026-08-21
- Admin console: added a Delete action to the Sync status table (`DELETE
  /api/v1/admin/connectors/repos?repo=`) — removes a repo's rows from the three typed
  projections (`pull_request_state`/`workflow_run_state`/`pull_request_review_state`) and any
  team mapping, so it drops out of Cockpit/Admin. Deliberately leaves `staging.raw_event`
  untouched (the immutable audit/replay source of truth) — this is a "stop showing it"
  operation, not an erasure; reconnecting later re-derives the same data. Audited as
  `REPO_DISCONNECTED`.
- Admin UI bug fixed: connecting a repo with a team assigned via the top form only refreshed the
  sync-status table, never the Teams data, so team repo-counts sat stale until a full reload —
  also added an explicit Refresh button to the Admin Teams overview as a manual fallback.
- Setup page: added a caption clarifying that "Time to value" is a one-time pilot-onboarding
  record (gap between this deployment's very first ingested event and its very first computed
  dashboard value, both from initial setup) — not a live metric that reacts to repos/teams
  connected later. Was confusing since it reads as a fixed ~50h no matter what you do now;
  check the Admin Sync status table for live progress on anything you connect today instead.

## 2026-08-20 (4)
- Frontend: the active sidebar tab was plain `useState` with no persistence, so any page refresh
  always dropped back to Cockpit regardless of where you were — now persisted in `sessionStorage`
  (same pattern the login session already uses), cleared on logout. Also added a matching manual
  Refresh button to the standalone Teams sidebar tab.

## 2026-08-20 (3)
- Admin console: redesigned the repo/team connect UI after it shipped confusing — one panel now
  drives create-team → connect-repo-with-team-assignment → bulk org-import as a numbered flow
  instead of three disconnected forms, with a hover "ⓘ" tooltip on every panel/section explaining
  what it does. Added a live Sync status table (repo, assigned team(s), Syncing/Synced/Failed
  badge, last-synced time, event count, per-repo Refresh button) backed by a new in-memory
  tracker in `ConnectorAdminService` so "did my connect actually work" doesn't require guessing
  from timestamps alone; polls every 15s only while something is actively syncing. Explicitly
  notes that Cockpit's DORA numbers lag a connected repo by up to 5 minutes (metrics-engine's
  real recompute interval) rather than updating instantly.

## 2026-08-20 (2)
- Admin console: connect a repo or team from the UI instead of a terminal call (PRD E1-S4/E8).
  New `POST /api/v1/admin/connectors/repos` and `/github-teams` (api-core) trigger
  connector-github's existing internal backfill endpoints asynchronously and audit the trigger
  (`REPO_CONNECT_TRIGGERED`/`GITHUB_TEAMS_CONNECT_TRIGGERED`); the Admin Connectors panel above
  already reflects progress once events land, so no separate job-status endpoint was needed.
  Also surfaced the previously-unused manual team/repo-membership API
  (`TeamAdminController`/`TeamAdminService`, `/api/v1/admin/teams/**`) in a new Teams panel —
  create a team by hand and map/unmap repos to it, alongside GitHub's automatic team import.
  Fixed a real bug found while wiring this up: `removeRepo`'s repo was a path variable, but a
  real repo name ("owner/repo") contains a slash a single path segment can't hold — changed to
  a query param. OpenAPI spec updated for all of the above (new + previously-undocumented
  `/admin/teams/**`).

## 2026-08-20
- New `connector-jenkins` service (PRD E1-S3, alt. CI/CD source alongside connector-github's
  GitHub Actions handling): polls a Jenkins job's build history and publishes `build.snapshot`
  events; `StagingEventWriter` now projects them into the existing provider-agnostic
  `staging.workflow_run_state` table (no schema change), normalizing Jenkins'
  `SUCCESS`/`FAILURE`/`UNSTABLE`/`ABORTED` vocabulary to the lowercase `success`/`failure`/
  `cancelled` metrics-engine's DORA queries already expect. Backfill/polling only, no webhook
  support yet. Verified end-to-end against a real local Jenkins instance (job `aie-pipeline`):
  5 builds backfilled, correct repo attribution and lowercase conclusions in
  `staging.workflow_run_state`. Also fixed local dev infra: `infra/docker-compose.yml`'s
  RabbitMQ management-UI port moved from 15672 to 25672 (host-port only) — 15672 sits inside a
  Windows Hyper-V/WSL dynamic port-exclusion range on some machines, blocking Docker from
  binding it; AMQP (5672, what the connectors actually use) was unaffected.

## 2026-08-10
- Security: removed two leaked GitHub PATs that had been committed in `README.md`'s local-dev
  notes; stopped tracking `.claude/settings.local.json` (machine-specific local paths) and added
  it to `.gitignore`. Rewrote `main`'s git history to scrub the tokens from all past commits —
  both tokens must still be revoked/rotated in GitHub settings since they were exposed publicly
  before the rewrite.

## 2026-08-06
- Frontend: applied the KPMG brand theme (matching the Reverse Engineering Platform app) across
  `frontend/`. Added `kpmg`/`cobalt` color scales anchored on KPMG Blue `#00338D` and KPMG Cobalt
  `#0091DA` (`tailwind.config.js`); rebuilt the authenticated app shell (`App.tsx`) with a KPMG
  masthead (blue bar, white "KPMG" badge) and a white sidebar with blue active-nav states,
  replacing the dark indigo/violet sidebar; added the same masthead to `Login.tsx` and a KPMG
  badge to the `Landing.tsx` header/footer; recolored the landing page's indigo/violet/fuchsia
  accents, gradients, and `GradientMeshBackground` canvas blobs/particle network to the KPMG
  blue/cobalt palette.

## 2026-07-27
- Docs: added `docs/01-product/frd.md` (Functional Requirements Document v1.0), deriving
  detailed input/processing/output/business-rule specs per function from the existing BRD
  FR-1.x IDs and PRD E1–E11 epics/stories; also published PDF renditions of the BRD summary,
  PRD, and new FRD (`BRD-Summary.pdf`, `PRD-v1.0.pdf`, `FRD-v1.0.pdf`) alongside the source
  markdown/docx in `docs/01-product/`.

## 2026-07-20
- Marketing landing page redesigned: new animated background (`GradientMeshBackground` — a
  canvas gradient mesh plus a cursor-reactive particle graph, replacing the static
  `AiNetworkBackground` SVG), GSAP + ScrollTrigger scroll choreography and count-up stats
  (`src/lib/motion.ts`), Lenis smooth scrolling, and `lucide-react` icons in place of the
  hand-rolled inline SVGs. New sections: tilting Executive Cockpit hero preview, trust
  strip ("no keystroke tracking / no manual tagging / read-only connectors" — BRD §5.3),
  and a "How it works" three-step. Frontend deps added: `gsap`, `lenis`, `lucide-react`.
  All motion is gated behind `prefers-reduced-motion`.

## 2026-07-15
- Dev tooling: added `infra/start-backend.sh` / `infra/stop-backend.sh` — a single command to
  build and start all 6 backend services (api-core, ingestion-writer, connector-github,
  connector-jira, metrics-engine, identity-service) plus infra, with health-check waits and
  per-service logs (`/tmp/aiimpacteval-<service>.log`); mirrors `infra/smoke-e2e.sh`'s
  boot pattern but leaves services running for local dev instead of tearing down. Root
  `README.md` and `infra/README.md` quick-start sections updated accordingly.

## 2026-07-14
- Phase 1 (E1-S4/E8 — Admin console wired to live data): new `GET /api/v1/admin/connectors`
  endpoint (ADMIN-only) derives per-connector status (Connected/Stale/Not connected), last-sync
  time, and event count from `staging.raw_event` timestamps — no manually-maintained status flag.
  Frontend `Admin.tsx` now fetches real data for the Connectors and Audit Log panels (replacing
  fixture data with `api.ts` calls, each panel marked "Live"); the Role assignments panel remains
  demo data with an explicit note, since no user directory/role persistence exists yet ahead of
  the OIDC login flow (ADR-0004). Closes out two of the three mock panels flagged in the
  2026-07-05 client-demo-prep entry below.
- Phase 1 (E1-S5 — 30-minute time-to-value): new `GET /api/v1/setup/status` endpoint
  (ADMIN/ENG_LEADER) derives a 4-item onboarding checklist (Git provider, ticketing, CI/CD,
  first populated dashboard) plus a `firstConnectionAt`→`firstDashboardAt` time-to-value figure
  against the 30-minute target — computed entirely from existing `staging.raw_event`/
  `mart.metric_daily` timestamps, no manually-maintained connector-status flag. New frontend
  "Setup" view renders the checklist and a progress bar against the target. Verified against the
  real pipeline via `infra/smoke-e2e.sh`: fresh DB shows all 4 items pending; after webhook/backfill
  events land, all 4 flip to done and `minutesToValue`/`withinTarget` populate correctly.

## 2026-07-06
- **Project renamed: Mallify → AI Impact Evaluation.** Full rebrand across the repo: Java base
  package `com.mallify` → `com.aiimpacteval` in all 7 Maven modules (directories moved, package/
  import declarations updated); root/module `groupId`/`artifactId`/`<name>` in every `pom.xml`;
  OpenAPI title; RabbitMQ exchange names `mallify.events`/`mallify.events.dlx` →
  `aiimpacteval.events`/`aiimpacteval.events.dlx`; dev JWT issuer `mallify-dev` →
  `aiimpacteval-dev`. Infra: Postgres/RabbitMQ DB name, user, and password
  (`mallify`/`mallify_local` → `aiimpacteval`/`aiimpacteval_local`) in `docker-compose.yml`,
  `.env(.example)`, and all 7 services' `application.yml` — **local Docker containers/volumes
  need to be recreated** (`docker compose down -v && docker compose up -d`) since the Postgres
  DB name changed. Frontend: npm package renamed, page title, logo assets renamed
  (`mallify-logo.png` → `ai-impact-evaluation-logo.png`, stray embedded XMP title metadata
  scrubbed), sessionStorage key, and all visible "Mallify" branding text. Docs: CLAUDE.md,
  README.md, all `docs/` files, and the PRD source docx filename updated; the BRD/PRD *content*
  (a signed stakeholder document) was left untouched — only its filename and doc-mirror text
  changed. `Mallify_Client_Presentation.pptx` was intentionally left as-is pending a separate
  pass. Historical entries below this one are left as written (they describe what shipped under
  the old name at the time).
- Teams real-data seeding: added `infra/generate-team-events.py` + `infra/seed-more-teams.sh`,
  which post 5 additional `team.snapshot` events (Payments, Platform, Growth, Mobile, Checkout —
  each with its own GitHub team id, repos, and members) plus ~30 days of per-team deploy/PR
  history, through the same real pipeline as the Cockpit seed script. `GET /api/v1/teams` now
  returns 6 teams (was 1) with repo counts from 1–3; deliberately varied deploy frequency/failure
  rate per team so drill-down DORA tiers differ meaningfully — verified Mobile lands Elite (34
  deploys, 8.8% CFR) and Platform lands Low (1 deploy, 243h lead time) — echoing the same
  team-performance narrative already used in the Investment Profile mock data. Re-runnable
  safely: team upsert is keyed on (source, source_id), distinct from smoke-e2e.sh's id 9001.
- Cockpit real-data seeding: added `infra/generate-seed-events.py` + `infra/seed-demo-history.sh`,
  which post ~30 days of synthetic GitHub Actions/PR webhook events (deterministic seed, varied
  deploy conclusions and PR lead times) through the real ingestion pipeline
  (connector-github -> RabbitMQ -> ingestion-writer -> staging.raw_event -> metrics-engine
  recompute), then trigger `/internal/recompute`. Unlike `infra/smoke-e2e.sh` (which intentionally
  posts one event per metric for pipeline testing), this gives Cockpit's DORA radar/trend/tier-band
  charts genuine day-over-day variation — verified via the Cockpit API: deployment_frequency and
  change_failure_rate now carry 24-point series (up from 1), MTTR/lead-time/PR-velocity/cycle-time
  similarly populated. No UI changes; this only adds real rows to `mart.metric_daily`. Re-runnable
  safely — delivery IDs are deterministic, so ingestion-writer's idempotency dedupes re-posts.
- New "AI Cost Track" sidebar view (E9 — AI Adoption & ROI): `frontend/src/views/AiCostTrack.tsx`
  with Spend / Adoption / Impact / ROI tabs — total AI spend, cost per PR / dev-day, spend-by-tool
  breakdown and trend, per-team adoption rate, AI-assisted vs. non-AI delta, and a $ ROI figure
  with visible/adjustable assumptions. UI-first against new mock data (`aiCostTrack` in
  `frontend/src/mock/mockData.ts`) ahead of the `ai-telemetry` connectors; formulas documented in
  `docs/01-product/metric-definitions.md` (AI-01..AI-05).
- Client-demo prep (richer mock datasets): expanded `frontend/src/mock/mockData.ts` for fuller
  charts — Investment Profile trend widened from 4 to 12 months and from 4 to 7 teams; Code
  Review reviewer load grew from 5 to 7 people and aging PRs from 3 to 6; Admin gained Jenkins,
  SonarQube (now connected), and the three AI-telemetry connectors named in the BRD's connector
  list (GitHub Copilot, Cursor, Claude Code), plus more role assignments and audit-log entries
  spanning two weeks instead of a few hours. Cockpit/Teams were left untouched — they read real
  data from api-core and have no mock fallback; the dev DB currently only has one seeded event
  per metric (via `infra/smoke-e2e.sh`), so their trend charts stay sparse until a richer
  synthetic-event seeding script exists.
- Landing page ambient background: added `frontend/src/components/AiNetworkBackground.tsx`, a
  fixed, sitewide, pointer-events-none decorative layer — a faint pulsing "neural network" of
  nodes and flowing connections (AI/ML motif) over slow-drifting indigo/violet/sky color blobs.
  Replaces the hero-only local blob/grid decoration; section backgrounds loosened to
  translucent so the effect reads consistently down the whole page, not just the hero. Two new
  Tailwind keyframes (`node-pulse`, `signal-flow`). Purely decorative, kept low-opacity so it
  doesn't compete with copy.
- Landing hero copy rewrite: leans into the brand name's etymology ("mall" + "-ify" — to
  gather many into one place) instead of the generic "source of truth" framing. Headline
  now reads "One roof for engineering delivery"; body explains the name and ties it to the
  same tool list/value prop as before (DORA, investment profile, code-review health, AI ROI).
- Browser tab favicon: added `frontend/public/mallify-logo.png` (Vite static asset) and a
  `<link rel="icon">` in `frontend/index.html` so the browser tab shows the real Mallify
  logo instead of the default Vite/browser icon.
- Real Mallify logo wired into the UI: added `frontend/src/assets/mallify-logo.png` (brand
  mark) and a `vite-env.d.ts` so TS recognizes image imports; the app-shell sidebar and the
  landing page navbar now render the actual logo image instead of the placeholder "M"
  gradient badge.
- Landing page navbar restyle: the sticky header is now dark-themed (same `#0a0e1a` +
  indigo/violet gradient logo badge as the app shell sidebar) instead of light/white, for
  visual consistency with the newly dark-themed app nav. Rest of the landing page (hero,
  features, pricing) is unchanged. Purely presentational.
- App shell sidebar/nav restyle: the sidebar (nav + user panel) is now dark-themed
  (near-black `#0a0e1a` with ambient indigo/violet glow accents) for a more premium feel,
  distinct from the light main content area. Added per-item nav icons, an active-item
  left accent bar + glow, a role badge pill (color-coded per RBAC role), and a redesigned
  user/logout card with avatar initials. Purely presentational — no changes to routing,
  auth, or data.

## 2026-07-05
- Client-demo prep (Cockpit cleanup): removed the "Data coverage by metric (sample size)" bar
  chart from Cockpit — the DORA radar/band charts and per-tile sample-size captions already
  cover that context, so the extra panel was redundant.
- Client-demo prep (DORA performance as charts): the "DORA performance" section now leads with
  two charts instead of number-only cards — a radar chart ("Overall DORA posture") scoring each
  of the 4 core metrics by its Elite/High/Medium/Low tier (Elite=100…Low=25), giving an at-a-
  glance shape of delivery health, plus a "Where each metric lands" panel with a four-segment
  Low→Medium→High→Elite band per metric, highlighted at the metric's current tier. The exact
  numbers/sparklines/deltas moved into a "Metric detail" section below the charts, so the charts
  are the headline and the numbers are the supporting detail.
- Client-demo prep (Cockpit redesign — corporate scorecard): replaced the gradient/emoji tile
  style with a flatter, enterprise-dashboard look: DORA metrics now carry a real
  Elite/High/Medium/Low performance tier per Google Cloud's 2023 State of DevOps thresholds
  (deployment frequency by deploys/day, lead time and MTTR by hour bands, change failure rate by
  percentage bands), shown as a colored tier badge and left accent bar per card — computed from
  the existing tile data, not fabricated. Split tiles into a "DORA performance" row (the 4 core
  metrics) and a "Review throughput" row (PR velocity/cycle time, untiered). Added a header
  toolbar (scope breadcrumb, window-length pill, disabled "Export report" placeholder). Donut,
  trend, and coverage panels restyled to match (slate borders, no shadows/gradients, uppercase
  micro-labels) — same pattern now used consistently across Cockpit and Teams drill-down.
- Client-demo prep (Cockpit chart panels + a real recharts bug fix): Cockpit now mirrors the
  Investment Profile page's chart-first layout — a "Deployment outcomes" donut (successful vs.
  failed/rolled-back, derived from `deployment_frequency`'s sample size × `change_failure_rate`'s
  rate — real data, no mock), the per-metric hero trend area chart in a 1/3+2/3 grid next to it,
  and a "Data coverage by metric" horizontal bar below. Also fixed a real bug hit while verifying
  this in-browser: every `Pie`/`Bar`/`Area` in the app (Cockpit, Investment Profile, Code Review,
  Teams, Landing) was silently rendering with an empty path — `isAnimationActive` defaults to
  true, and recharts 2.15.4's entry animation never resolved in this dev environment, leaving
  charts invisible with no console error. All chart primitives now set
  `isAnimationActive={false}`.
- Client-demo prep (Cockpit/Teams visualization): extracted Cockpit and Teams into
  `frontend/src/views/`; Cockpit tiles now use gradient-filled area sparklines, per-metric icons,
  and a trend delta badge (▲/▼ % vs. the start of the window, colored by whether that direction
  is good for the metric); added a full-width "Deployment frequency trend" hero chart below the
  tiles. Teams view adds a real "Repositories by team" bar chart (from the existing `repoCount`
  field) and redesigned team cards with a colored accent bar and hover affordance. All data is
  live from `/api/v1/metrics/cockpit` and `/api/v1/teams` — no mocks in this pass.
- Client-demo prep (landing page restyle): reworked the marketing landing page to a light,
  premium/corporate theme (white background, indigo/violet gradient accents) instead of the
  initial dark theme; added an "AI ROI" section (feature cards + an AI-assisted-spend-by-team
  chart with a floating ROI stat card) and a Pricing section (Monthly/Yearly toggle, three
  tiers) matching the Hivel-style reference the client liked. Login screen restyled to match.
- Client-demo prep (marketing landing + login): added a Hivel-style dark marketing landing page
  and a role-picker login screen in front of the app shell. Login uses the real dev-token bridge
  (`POST /api/v1/auth/dev-token`, ADR-0004) — not mocked — so the demo authenticates with an
  actual RBAC-scoped JWT for the chosen role (ADMIN/ENG_LEADER/MANAGER/IC/FINANCE_READONLY);
  session persists in `sessionStorage` and a sidebar "Log out" returns to the landing page.
- Client-demo prep (UI-first, backend to follow): Cockpit tiles now show a real trend sparkline
  from the existing `series` data; added three new frontend views ahead of their backend
  epics — Investment Profile (E5), Code Review Analytics (E6), and Admin & Access Console (E8,
  connectors/RBAC/audit) — all currently rendering fixture data from
  `frontend/src/mock/mockData.ts`, clearly labeled "Demo data" in the UI. Not wired to any API
  yet; each view's real backend is still to be implemented and will replace the mock import
  with an `api.ts` fetcher.
- Phase 1 (E2-S2/E4-S2 — team dimension & org→team drill-down): connector-github backfills org
  teams (repos + members); identity-service imports team structure into
  `core.team`/`core.team_repo`/`core.team_member`, resolving members through the existing
  identity resolver; metrics-engine computes all six metrics at repo/org/team scope (team
  percentiles from the raw per-event population, not repo-median averaging); new
  `GET /api/v1/teams` endpoint; Cockpit's `repo` param renamed to `scope` end-to-end
  (mart column renamed `repo`→`scope_id` + new `scope_type`); frontend Teams view lists teams
  and drills into team-scoped Cockpit tiles. 10-stage E2E smoke verifies the full loop live.
- Phase 1 (E3 — DORA complete): metrics-engine now computes all four DORA metrics — lead time
  for changes (DORA-2, heuristic PR-open→first prod deploy after merge), change failure rate
  (DORA-3, deploy followed within 48h by a hotfix/rollback), and MTTR (DORA-4) — alongside the
  existing deployment frequency and PR analytics. New tiles on the Cockpit (CFR shown as a
  sample-weighted rate). Detection patterns per-repo configurable.
- Phase 1 (E8): api-core now enforces RBAC — RS256 JWT resource server, five roles as
  authorities, `/api/v1/metrics/**` gated to analytical roles (IC denied), `/api/v1/audit/**`
  ADMIN-only. Append-only audit log with admin-only read; token issuance audited. Gated
  dev-token bridge pending OIDC (ADR-0004). Frontend acquires a dev token for local use.
- Phase 1 (E2/E3/E4 slice): metrics-engine materializes deployment frequency (DORA-1),
  PR velocity, and PR cycle-time p50 into the mart; Cockpit API (contract-first OpenAPI) and
  frontend tiles now show real data with freshness indicator; identity-service resolves
  GitHub/Jira identities into canonical contributors (email-merge heuristic, bot detection).
- PRD upgraded to v1.0 (stakeholder document by Vishal & Aditi): epic structure E1–E11 with user
  stories and acceptance criteria, personas/JTBD, North Star + instrumentation events, IA, UX
  requirements, open questions. Repo prd.md now mirrors the signed docx and tracks delivery
  status per epic.
- Phase 1 (F2/F3): Jira connector (token-verified webhooks + issue backfill with changelogs)
  and GitHub Actions workflow-run backfill in the GitHub connector; E2E smoke extended with
  the Jira leg.

## 2026-07-04
- Phase 1 (F1) first increment: GitHub connector (signature-verified webhooks + PR/commit
  backfill) and ingestion writer (idempotent queue→staging persistence with DLQ), shared
  event envelope contract (ADR-0003). End-to-end ingestion path is live.
- Phase 0 deliverables: PRD (Phase 1 MVP), DORA metric definitions v1, monorepo scaffold
  (frontend shell, api-core service with layered-schema Flyway baseline, docker-compose
  local infra for Postgres + RabbitMQ).
- Project foundation: documentation structure, engineering & security standards, baseline
  system architecture (C4), ADR-0001 (tech stack), ADR-0002 (queue-isolated connectors,
  single Postgres MVP), AI-agent documentation policy (CLAUDE.md).
