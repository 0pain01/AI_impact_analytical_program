# frontend

React + TypeScript (strict) + Tailwind dashboard app (C4 container "Web App").

**Status:** live and wired to api-core's real endpoints — Cockpit (DORA metrics, 30/90-day
window, CSV export), Teams drill-down, Investment Profile, Code Review Analytics, AI Cost Track
(spend/adoption/impact/ROI), Personal Activity, Setup/onboarding, and the Admin console
(connectors, repo/team management with delete, user administration, audit log), behind a
role-gated login. `src/api.ts` is a hand-written typed client mirroring api-core's OpenAPI
contract (`services/api-core/src/main/resources/openapi/api-core.yml`) — not code-generated;
keep it in sync by hand when the contract changes, per engineering standards §5.

## Run

```bash
npm install
npm run dev        # http://localhost:5173 (proxies /api → localhost:8080)
```

## Checks

```bash
npm run build      # tsc -b (strict) + vite build — must pass before merge
npm run lint       # eslint (config arrives with first real feature)
```

## Standards reminders (from docs/02-standards/engineering-standards.md)

- `strict: true`; no `any` without inline justification.
- Every dashboard widget implements loading / error / empty states.
- WCAG 2.1 AA; charts get accessible data-table fallbacks.
- `src/api.ts` types are kept in sync with `openapi/api-core.yml` by hand — update the spec first
  (contract-first), then mirror the change here.
