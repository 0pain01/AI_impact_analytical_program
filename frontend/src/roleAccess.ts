import type { Role } from './api'

export type View = 'cockpit' | 'teams' | 'investment' | 'code-review' | 'ai-cost' | 'personal' | 'setup' | 'admin'

// Role-aware navigation (PRD §8: "users see only surfaces their role and scope permit").
// This app only has 5 roles, so a couple of PRD personas fold together:
//   - MANAGER covers both "Engineering Manager" and "Product/Program Manager" (both
//     Team-scope in PRD §3 — there's no separate PM role here).
//   - ENG_LEADER covers both "Exec" and "Leader" access classes (both org-wide).
// ADMIN sees everything, including Admin itself. IC gets only Personal — every other tab is an
// org/team analytics surface IC is explicitly denied server-side (SecurityConfig), so showing
// them in the nav would just be a dead link.
export const TAB_ACCESS: Record<Role, View[]> = {
  ADMIN: ['cockpit', 'teams', 'investment', 'code-review', 'ai-cost', 'setup', 'admin'],
  ENG_LEADER: ['cockpit', 'teams', 'investment', 'ai-cost', 'setup'],
  MANAGER: ['teams', 'code-review', 'investment'],
  FINANCE_READONLY: ['investment', 'ai-cost'],
  IC: ['personal'],
}

export function defaultViewFor(role: Role): View {
  return TAB_ACCESS[role][0] ?? 'personal'
}