# Functional Specification — AI Impact Evaluation

**Status:** reflects the running system as of 2026-09-13. This is the authoritative "what the
product does" reference — condensed and reorganized from the signed BRD/PRD
([brd-summary.md](brd-summary.md), [prd.md](prd.md)) plus everything actually shipped since. For
formula-level detail behind any number mentioned here, see
[metric-definitions.md](metric-definitions.md); for how it's built, see the
[Technical Specification](../03-architecture/technical-specification.md).

## 1. Purpose

AI Impact Evaluation is an AI-native Software Engineering Intelligence (SEI) platform. It answers
three questions for an engineering organization, automatically, from data the org's existing
tools already produce:

1. **How fast and how safely does this team ship?** (DORA metrics)
2. **Where is engineering time actually going, and where does review work bottleneck?** (PR
   analytics, Investment Profile, Code Review Analytics)
3. **Is the org's investment in AI coding assistants paying off?** (AI Cost Track)

No engineer changes how they work to produce these numbers — every metric is derived from
commits, pull/merge requests, CI/CD runs, ticket transitions, and AI-assistant usage exports that
already exist as a byproduct of normal work (BRD §5.3, "no manual tagging").

## 2. Non-negotiable product rules

These come directly from the signed BRD and are treated as constraints on every feature, not
aspirations:

1. **No surveillance.** No keystroke tracking, no idle-time tracking, no screen monitoring, no
   individual-level metric that isn't opt-in and framed around growth. This ruled out entire
   categories of "engineering productivity" features other tools in this space ship.
2. **No manual tagging dependency.** If a metric requires an engineer to remember to label
   something, it isn't shipped until it can be derived automatically instead.
3. **Analytics layer only.** The platform never writes back to or replaces GitHub/GitLab/Jira/CI
   tools (the one planned exception, not yet built: a Phase 3 AI review agent posting PR
   comments).
4. **Least-privilege integrations.** Every connector requests the minimum OAuth/API scope it
   needs — e.g. a GitLab project token is provisioned with `read_api` and Reporter role, never
   write access, since the platform never writes to GitLab.
5. **Auditability.** Every configuration change, access grant, and data export is written to an
   append-only audit log, retained 12+ months.

## 3. Users and roles

Five roles, enforced server-side (not just hidden in the UI) on every request:

| Role | Who | Sees |
|---|---|---|
| **ADMIN** | Platform administrator | Everything: all dashboards org-wide, the Admin console (connector health, connect/disconnect repos, team management, user role assignment, audit log) |
| **ENG_LEADER** | CTO / VP Engineering | Every analytical dashboard, org-wide, read-only |
| **MANAGER** | Engineering Manager | Analytical dashboards **pinned to their own team** — a MANAGER cannot view another team's numbers by guessing a different scope value; the server resolves their scope from their own user record, not from a client-supplied parameter |
| **IC** | Individual Contributor / Tech Lead | **Personal Activity only**, and only after opting in (`core.contributor.opted_in_personal_view`) — no access to team or org dashboards at all, by design (rule 1 above) |
| **FINANCE_READONLY** | Finance / cost stakeholder | Analytical dashboards, read-only — intended primarily for AI Cost Track |

There is no "read-write" distinction beyond ADMIN — every non-admin role is inherently read-only
against the dashboards; only ADMIN can change configuration (connect a repo, assign a user's
role, delete a team).

## 4. Functional modules

### 4.1 Cockpit (DORA metrics)

**Purpose:** the primary "how are we doing" dashboard — all four DORA metrics plus PR-throughput
metrics, at a scope the viewer picks.

**Scope selector:** organization-wide (`*`), a specific team, or — as of 2026-09-12 — a single
repository directly (GitHub or GitLab), so a repo's own numbers no longer require first
assigning it to a team.

**What it shows, per scope, for a 30- or 90-day window (toggle):**
- **Deployment frequency** (DORA-1) — successful production deployments per day/week, with an
  Elite/High/Medium/Low performance band per the DORA benchmark thresholds.
- **Lead time for changes** (DORA-2) — median hours from a PR/MR opening to its first production
  deployment after merge.
- **Change failure rate** (DORA-3) — percentage of deployments followed within 48h by a
  hotfix/rollback.
- **Time to restore / MTTR** (DORA-4) — median time from a failed deployment to its remediating
  deploy.
- **PR velocity** — merged PRs/MRs per window.
- **PR cycle time** — median hours from PR/MR opened to merged.
- A daily time-series chart per metric, a CSV export of everything currently on screen, and an
  explicit `null`/"not available" state (never a fabricated number) when the underlying sample is
  too small or the data source doesn't support the metric yet (e.g. GitLab pipelines with no
  matching deploy-pattern configuration).

**Business rule:** team-level percentiles (lead time, MTTR, cycle time) are computed from the
underlying per-event population joined to the team's repos, never by averaging each repo's own
median — averaging medians across repos does not produce the team's true median, and doing so
would be a silent, hard-to-detect correctness bug in a number leadership makes decisions from.

### 4.2 Teams (org → team / org → repo drill-down)

**Purpose:** the navigation surface for Cockpit's scoped views.

- **Teams section:** every team (created manually in Admin, or imported automatically from a
  GitHub org's or GitLab group's real team/project structure), with a repo count and a bar chart
  of repos-per-team; clicking a team renders Cockpit scoped to it.
- **Repositories section:** every connected repository, GitHub or GitLab, each carrying a visible
  source badge; clicking one renders Cockpit scoped directly to that single repo. This is the
  primary way to demonstrate a specific repository's numbers without any team-assignment
  workaround.

### 4.3 Investment Profile

**Purpose:** classifies engineering work as **Planned**, **Unplanned**, or **Rework** by joining a
Jira issue key parsed out of a PR/MR's title against ingested Jira issue data (project, issue
type, and — for Rework specifically — whether the issue was reopened after being marked done).

**Business rule and known, intentional limitation:** a repository whose PRs don't reference a
connected Jira project's issue keys is genuinely, correctly reported as 100% "Unclassifiable" —
this is not a bug, it's the honest result of there being no linkage data to classify against. The
platform never guesses a classification from a PR title alone.

### 4.4 Code Review Analytics

**Purpose:** review-process health — where PRs/MRs actually spend time, and who's carrying the
review load.

- **Cycle-stage breakdown:** median hours for each of "open → first review," "first review →
  approval," and "approval → merge," computed identically for GitHub PR reviews and GitLab
  merge-request approvals (see §4.7 of the parity note below).
- **Reviewer load:** a leaderboard of who has reviewed the most PRs/MRs in the window.
- **Aging pull requests:** every currently-open PR/MR, sortable by age/repo/size, filterable by
  repo name substring — the "flag before they block release" worklist.

**GitHub/GitLab parity, and the one real difference:** every figure above is computed from the
same underlying table for both platforms. The one genuine platform difference, not a gap: GitLab
has no equivalent to GitHub's "changes requested"/"commented" review states — only "approved, by
whom, when" — so every GitLab review row reports as `APPROVED`.

### 4.5 AI Cost Track

**Purpose:** answers "is our investment in AI coding assistants paying off," combining spend,
adoption, and impact into one view.

- **AI-01 Total AI spend** — license/seat cost plus metered token cost, summed and broken out per
  tool (Claude Code, GitHub Copilot today).
- **AI-02 Cost per PR / cost per dev-day** — spend normalized by AI-assisted PR volume or active
  licensed engineers.
- **AI-03 Adoption rate** — share of active engineers with at least one AI-attributed commit/PR in
  the window, capped at 100% since Claude Code and Copilot identities aren't cross-tool resolved
  yet (one engineer using both tools currently counts as two "active" users in the raw data).
- **AI-04 AI-assisted vs. non-AI delta** — cycle time compared side by side for AI-attributed vs.
  non-AI-attributed PRs in the same window, with an explicit methodology/confounder caveat shown
  in-UI (this is a correlation, never presented as causation).
- **AI-05 Dollar ROI** — estimated hours saved (from the AI-04 cycle-time delta) converted to a
  dollar figure via an org-configurable blended hourly rate, plus a resulting ROI multiple.

**Trust rules, enforced in the API, not just the UI:** AI-04/AI-05 return `null` — never a
fabricated or zero-floored number — whenever a window's AI-assisted or non-AI-assisted merged-PR
sample is below 3. Every org-configurable assumption behind these numbers (Copilot seat cost,
licensed seat count, blended hourly rate, the 3-PR sample floor) is surfaced alongside the figure,
never hidden as a constant. The UI badges this data "Demo data · real API schema" rather than
"Live" as long as the underlying usage export is a sample file rather than a genuine enterprise
export — a distinction the platform is deliberately honest about rather than blurring.

### 4.6 Personal Activity

**Purpose:** an individual contributor's own activity trend — opt-in, self-scoped only, exactly
as BRD rule 1 (§2 above) requires. An IC who hasn't opted in sees nothing here; an IC can never
see anyone else's personal view, and no other role can substitute for an IC to view it on their
behalf.

### 4.7 Setup / Onboarding

**Purpose:** a checklist answering "have I actually connected everything, and how long did it
take to see a real number" — four booleans (git/ticketing/CI/dashboard connected) plus a
**time-to-value** figure: minutes from the org's first-ever ingested event to its first computed
dashboard metric, against a 30-minute target (BRD's onboarding NFR). Visible to ADMIN and
ENG_LEADER.

### 4.8 Admin Console

**Purpose:** the only place configuration changes happen — everything here is ADMIN-only and
every action is written to the audit log.

- **Connector health:** one row per data source (GitHub, GitHub Actions, GitLab, GitLab CI/CD,
  Jira, Jenkins), each with two distinct signals — **last checked** (did we hear from this
  connector at all, even a no-op re-check) vs. **last data change** (did anything actually
  change) — so a healthy connector with nothing new to report never reads as broken. Jira and
  Jenkins re-check themselves automatically on a schedule (every 30 minutes by default) precisely
  so this status never goes stale purely because nobody clicked "Refresh."
- **Connect a repo/project**, or **import a whole GitHub org's / GitLab group's** teams and repos
  in one action, from the UI — no terminal command required. **Connect a Jira project** or a
  **Jenkins job** the same way, in their own "Jira & Jenkins" section — minus team assignment,
  since neither has a project/job-to-team mapping yet.
- **Sync status** per connected repo, Jira project, or Jenkins job: Syncing / Synced / Failed,
  with a manual Refresh and Delete per row (delete removes only that source's own staging rows —
  `staging.raw_event` stays untouched).
- **Known, intentional-until-someone-hits-it limitation (all sources, not new to Jira/Jenkins):**
  Delete-then-Refresh on the *same* item does not reliably restore it. Every connector's backfill
  publishes events under a deterministic idempotency key (e.g. Jira's `issue:{id}:{updated}`,
  Jenkins' `jenkins:{job}:{buildNumber}`) — `staging.raw_event`'s own uniqueness constraint on
  that key silently skips reprocessing an event it has already stored, so if nothing changed
  upstream since the last successful sync, a fresh Refresh republishes the identical key, gets
  deduplicated, and the just-deleted projection row is never rebuilt. Recovery requires deleting
  the matching `staging.raw_event` rows too before re-triggering (an operator/support action, not
  exposed in the UI) — discovered verifying this Admin section's Jira/Jenkins parity work
  (2026-09-17), confirmed to affect GitHub/GitLab identically (their PR/commit sourceIds follow
  the same pattern), not something introduced by that change. Not fixed here: the right fix
  (should Delete also purge matching `raw_event` rows? should backfill support a "force
  republish" bypass?) is an architectural decision affecting every connector's idempotency
  contract (ADR-0003), not a one-line patch — needs its own ADR-level discussion before changing.
- **Team management:** create teams by hand, assign/unassign repos, delete a team (blocked with a
  clear error if a user or another team still depends on it, rather than silently breaking their
  access).
- **User administration:** assign a user's role and team pin.
- **Audit log:** every configuration change, access grant, and connect/disconnect/delete action,
  newest first, with actor, action, target, and before/after state where applicable.

### 4.9 Jira Work Items

**Purpose:** a Jira-specific detail view — what's actually in the backlog (items), what it's
tagged with (topics), and whether the team is keeping up with it — distinct from Investment
Profile's cross-tool Planned/Unplanned/Rework lens on the same underlying issue data.

- **KPIs:** open issue count, issues resolved in the window, median resolution time
  (created → resolved), reopen rate, and overdue count (open issues past their due date).
- **Pipeline shape:** issue counts by Jira's own To Do / In Progress / Done status category —
  stable across arbitrarily renamed custom workflow statuses.
- **Backlog composition:** open issues broken down by type and by priority.
- **Assignee workload:** a leaderboard of who is currently carrying the most open issues — the
  same "workload leaderboard, not individual surveillance" framing as Code Review's reviewer
  load (BRD rule 1, §2 above): a team-scoped count, never a keystroke/idle/activity signal.
- **Topics:** the most common Jira labels across the open backlog, sized by frequency.
- **Resolution trend:** median resolution time by week, over the window.
- **Issue worklist:** every open issue, searchable by key/summary, sortable by age/priority/
  project, filterable to one Jira project — the "flag before it slips" table, mirroring Code
  Review's aging-PRs worklist.

**Known, intentional limitation:** only standard Jira fields present on every instance regardless
of workflow or custom-field configuration are shown (priority, labels, reporter, due date, status
category). Story points and epic link are **not** surfaced — both live behind Jira custom field
IDs that vary per instance, and guessing one would mean silently mislabeling data on some
customers' Jira sites. Per the no-manual-tagging rule (§2 above), nothing here is derived from
anything an engineer has to enter beyond using Jira normally.

## 5. Data sources (what feeds the platform)

| Source | What's ingested | Live or backfill-only |
|---|---|---|
| GitHub | Pull requests, commits, PR reviews, org teams | Both (webhook + backfill) |
| GitHub Actions | Workflow runs / deployment statuses | Both |
| GitLab | Merge requests, commits, pipelines, MR approvals, group projects/members | Both |
| Jira | Issues with full status-transition history, priority, status category, reporter, labels, due date | Both |
| Jenkins | Build history per job | Backfill/polling only (no webhook trigger wired up yet) |
| Claude Code | Per-day usage/cost export | Backfill only (file-based today; see the Technical Specification for the real-API swap seam) |
| GitHub Copilot | Per-day usage export | Backfill only (same caveat as Claude Code) |

**Planned, not yet built** (named in the BRD, tracked for later phases): SonarQube (code
quality), PagerDuty/Opsgenie (incidents, which would sharpen change-failure-rate/MTTR from a
heuristic to a real incident-linked signal).

## 6. Out of scope for this version

- Writing back to any connected tool (beyond the not-yet-built Phase 3 review-comment agent).
- Cross-tool identity resolution for AI-assistant usage (an engineer using both Claude Code and
  Copilot is counted as two distinct "active AI users" until this is built).
- Incident-based change-failure-rate/MTTR (currently a hotfix/rollback-deploy heuristic).
- Sub-team hierarchy display (the data column exists; nothing populates or renders it yet).
- Multi-tenancy (the schema reserves a tenant-ID column; nothing enforces isolation on it today —
  this is a single-tenant deployment as it stands).
