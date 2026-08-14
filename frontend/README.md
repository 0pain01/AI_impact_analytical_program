# frontend

React + TypeScript (strict) + Tailwind dashboard app (C4 container "Web App").

**Status:** skeleton — app shell with Cockpit / Teams / Admin navigation and empty DORA
tiles. No API wiring yet (the typed client will be generated from api-core's OpenAPI spec —
contract-first, see engineering standards §5).

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
- No hand-written fetch types — generate from OpenAPI.
