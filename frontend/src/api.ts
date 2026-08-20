// Types mirror services/api-core/src/main/resources/openapi/api-core.yml (the contract).
// TODO(standards §3): replace with a client generated from the OpenAPI spec once codegen
// tooling lands in CI — do not let these drift by hand.
//
// Functions marked `// LIVE:` call the real api-core backend. A few views (Investment Profile,
// AI Cost Track) are still backed by deterministic mock data pending the Jira/AI-assistant
// connectors — see `frontend/src/mock/mockData.ts` and each view's own import for which.

const API_BASE_URL = 'http://localhost:8080'

export interface DailyValue {
  day: string
  value: number
}

export type MetricKey =
  | 'deployment_frequency'
  | 'lead_time_p50_hours'
  | 'change_failure_rate'
  | 'mttr_p50_hours'
  | 'pr_velocity'
  | 'pr_cycle_time_p50_hours'

export interface CockpitTile {
  metricKey: MetricKey
  label: string
  unit: string
  aggregation: 'sum' | 'latest' | 'rate'
  aggregate: number | null
  sampleSize: number
  definition: string
  series: DailyValue[]
}

export interface CockpitResponse {
  asOf: string | null
  windowDays: number
  scope: string
  tiles: CockpitTile[]
}

export interface Team {
  id: string
  name: string
  repoCount: number
}

export interface SetupChecklistItem {
  key: 'git' | 'ticketing' | 'ci' | 'dashboard'
  label: string
  done: boolean
  detail: string
}

export interface SetupStatus {
  checklist: SetupChecklistItem[]
  firstConnectionAt: string | null
  firstDashboardAt: string | null
  minutesToValue: number | null
  withinTarget: boolean | null
  targetMinutes: number
}

export interface ConnectorHealth {
  key: string
  name: string
  type: string
  status: 'CONNECTED' | 'STALE' | 'NOT_CONNECTED'
  lastSyncAt: string | null
  eventCount: number
}

export interface AuditEntry {
  id: number
  actorEmail: string | null
  action: string
  targetType: string
  targetId: string | null
  occurredAt: string
}

export interface PrCycleStage {
  stage: string
  hoursP50: number | null
}

export interface ReviewerLoad {
  reviewer: string
  reviews: number
}

export interface AgingPr {
  id: string
  title: string
  repo: string
  author: string
  ageHours: number
  reviewers: string[]
  sizeLines: number | null
}

export interface AgingPrsPage {
  items: AgingPr[]
  page: number
  pageSize: number
  totalCount: number
}

export interface CodeReviewResponse {
  windowLabel: string
  cycleStages: PrCycleStage[]
  reviewLoad: ReviewerLoad[]
  agingPrs: AgingPrsPage
}

export type Role = 'ADMIN' | 'ENG_LEADER' | 'MANAGER' | 'IC' | 'FINANCE_READONLY'

export interface Session {
  email: string
  role: Role
  token: string
}

const SESSION_KEY = 'ai-impact-evaluation.session'

// In-memory fallback in case sessionStorage is unavailable (private browsing, disabled
// storage, sandboxed iframe, etc.) — demo login must never fail regardless of the browser
// environment it's shown in.
let memorySession: Session | null = null

function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as Session) : memorySession
  } catch {
    return memorySession
  }
}

export function getSession(): Session | null {
  return readSession()
}

// LIVE: POST /api/v1/auth/dev-token — issues a JWT whose role/scope come from core.app_user
// (ADR-0004). No longer accepts a `role` — that used to be a client-supplied param, which meant
// anyone could self-declare ADMIN for any email. An admin now has to add the account first (see
// the Admin console's Users panel); login for an unknown email genuinely fails.
export async function login(email: string): Promise<Session> {
  const cleanEmail = (email ?? '').trim()
  if (!cleanEmail) {
    throw new Error('Enter your email to sign in.')
  }
  const params = new URLSearchParams({ email: cleanEmail })
  const res = await fetch(`${API_BASE_URL}/api/v1/auth/dev-token?${params.toString()}`, {
    method: 'POST',
  })
  if (!res.ok) {
    const detail = await res.text().catch(() => '')
    throw new Error(detail || `Login failed: ${res.status} ${res.statusText}`)
  }
  const issued: { token: string; role: string; expiresAt: string } = await res.json()

  const session: Session = { email: cleanEmail, role: issued.role as Role, token: issued.token }
  memorySession = session
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    // ignore — memorySession already covers this session for the page lifetime
  }
  return session
}

export function logout(): void {
  memorySession = null
  try {
    sessionStorage.removeItem(SESSION_KEY)
  } catch {
    // ignore
  }
}

/* ------------------------------------------------------------------ */
/* Deterministic mock data helpers                                     */
/* ------------------------------------------------------------------ */

export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const DEMO_TODAY = '2026-07-31'

function lastNDays(n: number, endIso: string): string[] {
  const end = new Date(`${endIso}T00:00:00Z`)
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(end)
    d.setUTCDate(end.getUTCDate() - (n - 1 - i))
    return d.toISOString().slice(0, 10)
  })
}

/** Deterministic ramp + wobble series (no RNG, so the demo is stable across reloads). */
function buildSeries(
  days: string[],
  start: number,
  end: number,
  wobbleAmp: number,
  phase: number,
  round: (n: number) => number = (n) => Math.round(n * 10) / 10,
): DailyValue[] {
  const n = days.length
  return days.map((day, i) => {
    const t = n === 1 ? 1 : i / (n - 1)
    const trend = start + (end - start) * t
    const wobble = 1 + wobbleAmp * Math.sin(i * 1.1 + phase)
    return { day, value: round(trend * wobble) }
  })
}

// No longer used by fetchCockpit (now calls the real API) — exported as a reference/demo-data
// generator for other still-mocked views that may want similar shapes.
export function buildCockpitTiles(windowDays: number): CockpitTile[] {
  const days = lastNDays(windowDays, DEMO_TODAY)

  const deployCount = Math.round(windowDays * 1.93)
  const prCount = Math.round(windowDays * 7.13)
  const incidentCount = Math.round(windowDays * 0.3)

  return [
    {
      metricKey: 'deployment_frequency',
      label: 'Deployment frequency',
      unit: 'per day',
      aggregation: 'rate',
      aggregate: Number((deployCount / windowDays).toFixed(2)),
      sampleSize: deployCount,
      definition: 'Successful production deploys per day, derived from CI/CD deploy events (FR-3.1).',
      series: buildSeries(days, 1.4, 2.3, 0.18, 0, (n) => Math.max(0, Math.round(n))),
    },
    {
      metricKey: 'lead_time_p50_hours',
      label: 'Lead time for changes (p50)',
      unit: 'hrs',
      aggregation: 'latest',
      aggregate: 32.4,
      sampleSize: deployCount,
      definition: 'Median hours from first commit to production deploy (FR-3.2).',
      series: buildSeries(days, 41, 30, 0.12, 1),
    },
    {
      metricKey: 'change_failure_rate',
      label: 'Change failure rate',
      unit: '%',
      aggregation: 'rate',
      aggregate: 0.18,
      sampleSize: deployCount,
      definition: 'Share of deploys requiring a hotfix or rollback within 48h (FR-3.3).',
      series: buildSeries(days, 0.24, 0.16, 0.15, 2, (n) => Math.round(n * 1000) / 1000),
    },
    {
      metricKey: 'mttr_p50_hours',
      label: 'Mean time to restore (p50)',
      unit: 'hrs',
      aggregation: 'latest',
      aggregate: 4.6,
      sampleSize: incidentCount,
      definition: 'Median hours from incident open to resolution, from PagerDuty (FR-3.4).',
      series: buildSeries(days, 6.8, 4.2, 0.2, 3),
    },
    {
      metricKey: 'pr_velocity',
      label: 'PR velocity',
      unit: 'PRs/day',
      aggregation: 'rate',
      aggregate: Number((prCount / windowDays).toFixed(2)),
      sampleSize: prCount,
      definition: 'Merged pull requests per day across connected repos.',
      series: buildSeries(days, 5.6, 7.9, 0.16, 4, (n) => Math.max(0, Math.round(n))),
    },
    {
      metricKey: 'pr_cycle_time_p50_hours',
      label: 'PR cycle time (p50)',
      unit: 'hrs',
      aggregation: 'latest',
      aggregate: 16.2,
      sampleSize: prCount,
      definition: 'Median hours from PR open to merge.',
      series: buildSeries(days, 21, 15, 0.14, 5),
    },
  ]
}

export const MOCK_TEAMS: Team[] = [
  { id: 'payments', name: 'Payments', repoCount: 6 },
  { id: 'platform', name: 'Platform', repoCount: 9 },
  { id: 'growth', name: 'Growth', repoCount: 4 },
  { id: 'mobile', name: 'Mobile', repoCount: 5 },
  { id: 'checkout', name: 'Checkout', repoCount: 3 },
  { id: 'data-ml', name: 'Data & ML', repoCount: 7 },
  { id: 'identity', name: 'Identity', repoCount: 4 },
]

export const MOCK_SETUP_STATUS: SetupStatus = {
  checklist: [
    { key: 'git', label: 'Connect source control', done: true, detail: 'GitHub org connected — 38 repos syncing' },
    { key: 'ticketing', label: 'Connect ticketing', done: true, detail: 'Jira Cloud connected — 6 projects' },
    { key: 'ci', label: 'Connect CI/CD', done: true, detail: 'GitHub Actions + Jenkins connected' },
    { key: 'dashboard', label: 'View first dashboard', done: true, detail: 'Cockpit populated with 30 days of history' },
  ],
  firstConnectionAt: '2026-07-01T09:14:00Z',
  firstDashboardAt: '2026-07-01T09:41:00Z',
  minutesToValue: 27,
  withinTarget: true,
  targetMinutes: 30,
}

export const MOCK_CONNECTORS: ConnectorHealth[] = [
  { key: 'github', name: 'GitHub', type: 'Git host', status: 'CONNECTED', lastSyncAt: '2026-07-31T07:55:00Z', eventCount: 48213 },
  { key: 'jira', name: 'Jira', type: 'Ticketing', status: 'CONNECTED', lastSyncAt: '2026-07-31T07:50:00Z', eventCount: 9820 },
  { key: 'gha', name: 'GitHub Actions', type: 'CI/CD', status: 'CONNECTED', lastSyncAt: '2026-07-31T07:48:00Z', eventCount: 15230 },
  { key: 'jenkins', name: 'Jenkins', type: 'CI/CD', status: 'CONNECTED', lastSyncAt: '2026-07-31T07:40:00Z', eventCount: 3110 },
  { key: 'sonarqube', name: 'SonarQube', type: 'Code quality', status: 'STALE', lastSyncAt: '2026-07-29T06:10:00Z', eventCount: 6120 },
  { key: 'pagerduty', name: 'PagerDuty', type: 'Incidents', status: 'NOT_CONNECTED', lastSyncAt: null, eventCount: 0 },
  { key: 'copilot', name: 'GitHub Copilot', type: 'AI telemetry', status: 'CONNECTED', lastSyncAt: '2026-07-31T07:35:00Z', eventCount: 4021 },
  { key: 'cursor', name: 'Cursor', type: 'AI telemetry', status: 'NOT_CONNECTED', lastSyncAt: null, eventCount: 0 },
  { key: 'claude-code', name: 'Claude Code', type: 'AI telemetry', status: 'CONNECTED', lastSyncAt: '2026-07-31T07:37:00Z', eventCount: 8890 },
]

export const MOCK_AUDIT_LOG: AuditEntry[] = [
  { id: 1042, actorEmail: 'vp.eng@client.com', action: 'connector.connected', targetType: 'connector', targetId: 'GitHub', occurredAt: '2026-07-31T07:55:00Z' },
  { id: 1041, actorEmail: 'vp.eng@client.com', action: 'connector.connected', targetType: 'connector', targetId: 'Claude Code', occurredAt: '2026-07-31T07:37:00Z' },
  { id: 1040, actorEmail: 'system', action: 'connector.health_alert', targetType: 'connector', targetId: 'SonarQube', occurredAt: '2026-07-29T06:11:00Z' },
  { id: 1039, actorEmail: 'manager.platform@client.com', action: 'report.exported', targetType: 'dashboard', targetId: 'Cockpit — Platform team', occurredAt: '2026-07-28T15:31:00Z' },
  { id: 1038, actorEmail: 'vp.eng@client.com', action: 'role.granted', targetType: 'role', targetId: 'finance@client.com → Finance', occurredAt: '2026-07-27T11:02:00Z' },
  { id: 1037, actorEmail: 'manager.payments@client.com', action: 'goal.created', targetType: 'goal', targetId: 'Reduce PR cycle time to <24h — Payments', occurredAt: '2026-07-26T09:44:00Z' },
  { id: 1036, actorEmail: 'security@client.com', action: 'role.granted', targetType: 'role', targetId: 'lead.checkout@client.com → Tech Lead', occurredAt: '2026-07-24T17:20:00Z' },
  { id: 1035, actorEmail: 'lead.payments@client.com', action: 'pr_flag.actioned', targetType: 'pull_request', targetId: 'PR-4821 — connector-github', occurredAt: '2026-07-22T13:05:00Z' },
  { id: 1034, actorEmail: 'vp.eng@client.com', action: 'connector.connected', targetType: 'connector', targetId: 'SonarQube', occurredAt: '2026-07-20T10:12:00Z' },
  { id: 1033, actorEmail: 'system', action: 'export.data_deleted', targetType: 'export', targetId: 'export-88213', occurredAt: '2026-07-18T04:00:00Z' },
]

/* ------------------------------------------------------------------ */
/* Exported fetchers (same signatures as the live api-core client)     */
/* ------------------------------------------------------------------ */

async function authFetch(path: string, init?: { method?: string; body?: unknown }): Promise<Response> {
  const session = readSession()
  if (!session?.token) {
    throw new Error('Not logged in — no token available')
  }
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: init?.method ?? 'GET',
    headers: {
      Authorization: `Bearer ${session.token}`,
      ...(init?.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
  })
  if (!res.ok) {
    const detail = await res.text().catch(() => '')
    throw new Error(`Request failed: ${res.status} ${res.statusText}${detail ? ` — ${detail}` : ''}`)
  }
  return res
}

// LIVE: GET /api/v1/metrics/cockpit?days=&scope=
export async function fetchCockpit(days = 30, scope = '*'): Promise<CockpitResponse> {
  const params = new URLSearchParams({ days: String(days), scope })
  const res = await authFetch(`/api/v1/metrics/cockpit?${params.toString()}`)
  return res.json()
}

// LIVE: GET /api/v1/teams
export async function fetchTeams(): Promise<Team[]> {
  const res = await authFetch('/api/v1/teams')
  return res.json()
}

// LIVE: GET /api/v1/setup/status
export async function fetchSetupStatus(): Promise<SetupStatus> {
  const res = await authFetch('/api/v1/setup/status')
  return res.json()
}

// LIVE: GET /api/v1/admin/connectors
export async function fetchAdminConnectors(): Promise<ConnectorHealth[]> {
  const res = await authFetch('/api/v1/admin/connectors')
  return res.json()
}

// LIVE: GET /api/v1/audit?limit=
export async function fetchAuditLog(limit = 20): Promise<AuditEntry[]> {
  const params = new URLSearchParams({ limit: String(limit) })
  const res = await authFetch(`/api/v1/audit?${params.toString()}`)
  return res.json()
}

// LIVE: GET /api/v1/metrics/code-review?days=&scope=&repo=&sortBy=&sortDir=&page=&pageSize=
export interface CodeReviewParams {
  days?: number
  scope?: string
  repo?: string
  sortBy?: 'age' | 'repo'
  sortDir?: 'asc' | 'desc'
  page?: number
  pageSize?: number
}

export async function fetchCodeReview(params: CodeReviewParams = {}): Promise<CodeReviewResponse> {
  const { days = 30, scope = '*', repo, sortBy = 'age', sortDir = 'desc', page = 0, pageSize = 20 } = params
  const query = new URLSearchParams({
    days: String(days),
    scope,
    sortBy,
    sortDir,
    page: String(page),
    pageSize: String(pageSize),
  })
  if (repo) query.set('repo', repo)
  const res = await authFetch(`/api/v1/metrics/code-review?${query.toString()}`)
  return res.json()
}

// LIVE: GET/POST/PATCH /api/v1/admin/users/** — the real access-control record behind login()
// and ScopeResolver's server-side scope enforcement.
export interface AdminUser {
  id: string
  email: string
  displayName: string
  role: Role
  teamId: string | null
  teamName: string | null
  githubLogin: string | null
  active: boolean
  lastLoginAt: string | null
}

export async function fetchAdminUsers(): Promise<AdminUser[]> {
  const res = await authFetch('/api/v1/admin/users')
  return res.json()
}

export async function createAdminUser(
  email: string,
  displayName: string,
  role: Role,
  teamId: string | null,
  githubLogin: string | null,
): Promise<AdminUser> {
  const res = await authFetch('/api/v1/admin/users', {
    method: 'POST',
    body: { email, displayName, role, teamId, githubLogin },
  })
  return res.json()
}

export async function updateAdminUserRole(userId: string, role: Role, teamId: string | null): Promise<AdminUser> {
  const res = await authFetch(`/api/v1/admin/users/${userId}/role`, {
    method: 'PATCH',
    body: { role, teamId },
  })
  return res.json()
}

export async function updateAdminUserGithubLogin(userId: string, githubLogin: string | null): Promise<AdminUser> {
  const res = await authFetch(`/api/v1/admin/users/${userId}/github-login`, {
    method: 'PATCH',
    body: { githubLogin },
  })
  return res.json()
}

export async function setAdminUserActive(userId: string, active: boolean): Promise<AdminUser> {
  const res = await authFetch(`/api/v1/admin/users/${userId}/active`, {
    method: 'PATCH',
    body: { active },
  })
  return res.json()
}

// LIVE: POST /api/v1/admin/connectors/repos — triggers connector-github's backfill for one
// repo (what a terminal `curl -X POST .../internal/backfill?owner=&repo=` did manually before),
// optionally assigning it to a team in the same call. Runs async server-side; poll
// fetchRepoSyncStatus() for live progress.
export async function connectRepo(owner: string, repo: string, teamId: string | null): Promise<void> {
  await authFetch('/api/v1/admin/connectors/repos', { method: 'POST', body: { owner, repo, teamId } })
}

// LIVE: GET /api/v1/admin/connectors/repos — live sync status for every repo that's ever
// synced or was just triggered: last sync time, event count, IN_PROGRESS/COMPLETED/FAILED,
// and which team(s) it's mapped to.
export interface RepoSyncStatus {
  repo: string
  lastSyncAt: string | null
  eventCount: number
  syncState: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
  syncError: string | null
  teams: string[]
}

export async function fetchRepoSyncStatus(): Promise<RepoSyncStatus[]> {
  const res = await authFetch('/api/v1/admin/connectors/repos')
  return res.json()
}

// LIVE: DELETE /api/v1/admin/connectors/repos?repo= — removes a repo from Cockpit/Admin (the
// typed projection tables + any team mapping). Does NOT touch the immutable raw event log —
// re-connecting the same repo later re-derives the same data.
export async function disconnectRepo(repo: string): Promise<void> {
  const params = new URLSearchParams({ repo })
  await authFetch(`/api/v1/admin/connectors/repos?${params.toString()}`, { method: 'DELETE' })
}

// LIVE: POST /api/v1/admin/connectors/github-teams — triggers connector-github's org team
// import (repos + members), same as connectRepo but for `/internal/backfill-teams?org=`.
export async function connectGithubOrgTeams(org: string): Promise<void> {
  await authFetch('/api/v1/admin/connectors/github-teams', { method: 'POST', body: { org } })
}

// LIVE: POST/GET/DELETE /api/v1/admin/teams/** — manual team/repo-structure administration
// (complements automatic GitHub team import above): create a team by hand and map repos to it.
export async function createOrUpdateTeam(name: string, parentTeamId: string | null): Promise<{ id: string }> {
  const res = await authFetch('/api/v1/admin/teams', { method: 'POST', body: { name, parentTeamId } })
  return res.json()
}

export async function listTeamRepos(teamId: string): Promise<string[]> {
  const res = await authFetch(`/api/v1/admin/teams/${teamId}/repos`)
  return res.json()
}

export async function addTeamRepo(teamId: string, repo: string): Promise<string[]> {
  const res = await authFetch(`/api/v1/admin/teams/${teamId}/repos`, { method: 'POST', body: { repo } })
  return res.json()
}

export async function removeTeamRepo(teamId: string, repo: string): Promise<string[]> {
  const params = new URLSearchParams({ repo })
  const res = await authFetch(`/api/v1/admin/teams/${teamId}/repos?${params.toString()}`, { method: 'DELETE' })
  return res.json()
}

// LIVE: GET /api/v1/personal/activity — the IC role's self-scoped Personal Activity tab. No
// scope/repo/team params exist for this one on purpose: the server resolves "you" from your
// login, not from anything the client sends.
export interface OwnPr {
  id: string
  title: string
  repo: string
  ageHours: number
}

export interface ReviewGiven {
  repo: string
  prId: string
  state: string
  submittedAt: string | null
}

export interface PersonalActivity {
  githubLogin: string | null
  openPrs: OwnPr[]
  recentReviewsGiven: ReviewGiven[]
}

export async function fetchPersonalActivity(): Promise<PersonalActivity> {
  const res = await authFetch('/api/v1/personal/activity')
  return res.json()
}

// LIVE: GET /api/v1/metrics/investment-profile?days=&scope=
export interface CategoryCount {
  category: string
  count: number
}

export interface MonthlyBreakdown {
  month: string
  planned: number
  unplanned: number
  rework: number
  unclassifiable: number
}

export interface TeamBreakdown {
  team: string
  planned: number
  unplanned: number
  rework: number
  unclassifiable: number
}

export interface InvestmentProfileResponse {
  windowLabel: string
  breakdown: CategoryCount[]
  trend: MonthlyBreakdown[]
  byTeam: TeamBreakdown[]
}

export async function fetchInvestmentProfile(days = 90, scope = '*'): Promise<InvestmentProfileResponse> {
  const params = new URLSearchParams({ days: String(days), scope })
  const res = await authFetch(`/api/v1/metrics/investment-profile?${params.toString()}`)
  return res.json()
}