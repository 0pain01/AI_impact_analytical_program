// DEMO-ONLY MOCK DATA (temporary, per UI-first-then-backend plan discussed with the client).
// Shapes here anticipate the real E5/E6/E8 API contracts so swapping to `api.ts` fetchers later
// is a drop-in replacement, not a rewrite. Do not wire these into production builds.

export interface InvestmentSlice {
  category: 'New features' | 'Bug fixes' | 'Tech debt' | 'Unplanned / firefighting'
  hours: number
}

export interface InvestmentTrendPoint {
  month: string
  planned: number
  unplanned: number
}

export const investmentProfile = {
  windowLabel: 'Last 12 months · Org-wide',
  breakdown: [
    { category: 'New features', hours: 3120 },
    { category: 'Bug fixes', hours: 1180 },
    { category: 'Tech debt', hours: 640 },
    { category: 'Unplanned / firefighting', hours: 410 },
  ] as InvestmentSlice[],
  trend: [
    { month: 'Aug', planned: 85, unplanned: 15 },
    { month: 'Sep', planned: 84, unplanned: 16 },
    { month: 'Oct', planned: 81, unplanned: 19 },
    { month: 'Nov', planned: 80, unplanned: 20 },
    { month: 'Dec', planned: 83, unplanned: 17 },
    { month: 'Jan', planned: 79, unplanned: 21 },
    { month: 'Feb', planned: 77, unplanned: 23 },
    { month: 'Mar', planned: 78, unplanned: 22 },
    { month: 'Apr', planned: 82, unplanned: 18 },
    { month: 'May', planned: 79, unplanned: 21 },
    { month: 'Jun', planned: 74, unplanned: 26 },
    { month: 'Jul', planned: 71, unplanned: 29 },
  ] as InvestmentTrendPoint[],
  byTeam: [
    { team: 'Payments', planned: 88, unplanned: 12 },
    { team: 'Platform', planned: 63, unplanned: 37 },
    { team: 'Growth', planned: 76, unplanned: 24 },
    { team: 'Mobile', planned: 91, unplanned: 9 },
    { team: 'Checkout', planned: 84, unplanned: 16 },
    { team: 'Data & ML', planned: 69, unplanned: 31 },
    { team: 'Identity', planned: 87, unplanned: 13 },
  ],
}

export interface PrCycleStage {
  stage: 'Open → first review' | 'First review → approval' | 'Approval → merge'
  hoursP50: number
}

export interface AgingPr {
  id: string
  title: string
  repo: string
  author: string
  ageHours: number
  reviewers: string[]
  sizeLines: number
}

export const codeReview = {
  windowLabel: 'Last 30 days · Org-wide',
  cycleStages: [
    { stage: 'Open → first review', hoursP50: 4.2 },
    { stage: 'First review → approval', hoursP50: 9.8 },
    { stage: 'Approval → merge', hoursP50: 2.1 },
  ] as PrCycleStage[],
  reviewLoad: [
    { reviewer: 'A. Rao', reviews: 34 },
    { reviewer: 'K. Singh', reviews: 29 },
    { reviewer: 'M. Chen', reviews: 27 },
    { reviewer: 'J. Alvarez', reviews: 18 },
    { reviewer: 'P. Nair', reviews: 12 },
    { reviewer: 'S. Okafor', reviews: 22 },
    { reviewer: 'L. Fischer', reviews: 15 },
  ],
  agingPrs: [
    { id: 'PR-4821', title: 'Refactor ingestion retry policy', repo: 'connector-github', author: 'j.alvarez', ageHours: 96, reviewers: ['K. Singh'], sizeLines: 812 },
    { id: 'PR-4796', title: 'Add SonarQube quality gate mapping', repo: 'metrics-engine', author: 'p.nair', ageHours: 72, reviewers: ['A. Rao', 'M. Chen'], sizeLines: 340 },
    { id: 'PR-4780', title: 'Team scope mart migration', repo: 'api-core', author: 'm.chen', ageHours: 51, reviewers: ['K. Singh'], sizeLines: 205 },
    { id: 'PR-4762', title: 'Backfill PagerDuty incident history', repo: 'connector-pagerduty', author: 's.okafor', ageHours: 44, reviewers: ['L. Fischer', 'P. Nair'], sizeLines: 168 },
    { id: 'PR-4741', title: 'AI ROI cost-per-seat calculation', repo: 'metrics-engine', author: 'l.fischer', ageHours: 38, reviewers: ['A. Rao'], sizeLines: 522 },
    { id: 'PR-4725', title: 'Investment Profile category mapper', repo: 'api-core', author: 'k.singh', ageHours: 29, reviewers: ['M. Chen', 'S. Okafor'], sizeLines: 276 },
  ] as AgingPr[],
}

// AI Cost Track (E9) is now backed by a real API — see AiCostTrack.tsx's fetchAiCostTrack call —
// computed from staging.ai_usage_state (connector-ai-telemetry ingests real Claude Code/Copilot
// usage reports). The mock aiCostTrack export that used to live here was removed since nothing
// references it anymore; see docs/CHANGELOG.md for when that swap happened.

export interface ConnectorStatus {
  name: string
  type: 'Git host' | 'Ticketing' | 'CI/CD' | 'Code quality' | 'Incidents' | 'AI telemetry'
  status: 'Connected' | 'Error' | 'Not connected'
  scopes: string[]
  lastSync: string | null
}

export interface RoleAssignment {
  email: string
  role: 'CTO / VP Eng' | 'Manager' | 'Tech Lead' | 'IC' | 'Finance' | 'Compliance'
  scope: string
}

export interface AuditEntry {
  timestamp: string
  actor: string
  action: string
  target: string
}

export const admin = {
  connectors: [
    { name: 'GitHub', type: 'Git host', status: 'Connected', scopes: ['repo:read', 'read:org'], lastSync: '2026-07-05T08:12:00Z' },
    { name: 'Jira', type: 'Ticketing', status: 'Connected', scopes: ['read:jira-work'], lastSync: '2026-07-05T08:10:00Z' },
    { name: 'GitHub Actions', type: 'CI/CD', status: 'Connected', scopes: ['actions:read'], lastSync: '2026-07-05T07:55:00Z' },
    { name: 'Jenkins', type: 'CI/CD', status: 'Connected', scopes: ['build:read'], lastSync: '2026-07-05T07:50:00Z' },
    { name: 'SonarQube', type: 'Code quality', status: 'Connected', scopes: ['quality-gate:read'], lastSync: '2026-07-05T06:40:00Z' },
    { name: 'PagerDuty', type: 'Incidents', status: 'Error', scopes: ['incidents:read'], lastSync: '2026-07-04T22:03:00Z' },
    { name: 'GitHub Copilot', type: 'AI telemetry', status: 'Connected', scopes: ['usage:read'], lastSync: '2026-07-05T08:05:00Z' },
    { name: 'Cursor', type: 'AI telemetry', status: 'Not connected', scopes: [], lastSync: null },
    { name: 'Claude Code', type: 'AI telemetry', status: 'Connected', scopes: ['usage:read'], lastSync: '2026-07-05T08:07:00Z' },
  ] as ConnectorStatus[],
  roles: [
    { email: 'vp.eng@client.com', role: 'CTO / VP Eng', scope: 'Org-wide' },
    { email: 'manager.platform@client.com', role: 'Manager', scope: 'Platform team' },
    { email: 'manager.payments@client.com', role: 'Manager', scope: 'Payments team' },
    { email: 'lead.payments@client.com', role: 'Tech Lead', scope: 'Payments team' },
    { email: 'lead.checkout@client.com', role: 'Tech Lead', scope: 'Checkout team' },
    { email: 'finance@client.com', role: 'Finance', scope: 'Org-wide (read-only, aggregated)' },
    { email: 'security@client.com', role: 'Compliance', scope: 'Org-wide (admin console)' },
  ] as RoleAssignment[],
  auditLog: [
    { timestamp: '2026-07-05T08:12:00Z', actor: 'vp.eng@client.com', action: 'connector.connected', target: 'GitHub' },
    { timestamp: '2026-07-05T08:07:00Z', actor: 'vp.eng@client.com', action: 'connector.connected', target: 'Claude Code' },
    { timestamp: '2026-07-04T22:04:00Z', actor: 'system', action: 'connector.health_alert', target: 'PagerDuty' },
    { timestamp: '2026-07-03T15:31:00Z', actor: 'manager.platform@client.com', action: 'report.exported', target: 'Cockpit — Platform team' },
    { timestamp: '2026-07-02T11:02:00Z', actor: 'vp.eng@client.com', action: 'role.granted', target: 'finance@client.com → Finance' },
    { timestamp: '2026-07-01T09:44:00Z', actor: 'manager.payments@client.com', action: 'goal.created', target: 'Reduce PR cycle time to <24h — Payments' },
    { timestamp: '2026-06-29T17:20:00Z', actor: 'security@client.com', action: 'role.granted', target: 'lead.checkout@client.com → Tech Lead' },
    { timestamp: '2026-06-27T13:05:00Z', actor: 'lead.payments@client.com', action: 'pr_flag.actioned', target: 'PR-4821 — connector-github' },
    { timestamp: '2026-06-25T10:12:00Z', actor: 'vp.eng@client.com', action: 'connector.connected', target: 'SonarQube' },
  ] as AuditEntry[],
}
