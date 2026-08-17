import { useState } from 'react'
import { getSession, logout, type Session } from './api'
import { TAB_ACCESS, defaultViewFor, type View } from './roleAccess'
import aiImpactEvaluationLogo from './assets/ai-impact-evaluation-logo.png'
import Cockpit from './views/Cockpit'
import Teams from './views/Teams'
import InvestmentProfile from './views/InvestmentProfile'
import CodeReview from './views/CodeReview'
import AiCostTrack from './views/AiCostTrack'
import Personal from './views/Personal'
import Admin from './views/Admin'
import Setup from './views/Setup'
import Landing from './views/Landing'
import Login from './views/Login'

function NavIcon({ id }: { id: View }) {
  const common = { width: 18, height: 18, viewBox: '0 0 24 24', fill: 'none', strokeWidth: 1.75, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const }
  switch (id) {
    case 'cockpit':
      return (
        <svg {...common} stroke="currentColor">
          <path d="M3 12a9 9 0 1 1 18 0" />
          <path d="M12 12l4-4.5" />
          <path d="M12 21v-1.5M4.2 19.5l1.1-1.1M19.8 19.5l-1.1-1.1" />
        </svg>
      )
    case 'teams':
      return (
        <svg {...common} stroke="currentColor">
          <circle cx="9" cy="8" r="3" />
          <path d="M2.5 20a6.5 6.5 0 0 1 13 0" />
          <circle cx="17.5" cy="9.5" r="2.5" />
          <path d="M15.5 20a5 5 0 0 1 6-4.9" />
        </svg>
      )
    case 'investment':
      return (
        <svg {...common} stroke="currentColor">
          <path d="M12 3v9l6.4 3.7" />
          <path d="M20.9 13.5A9 9 0 1 1 10.5 3.1" />
        </svg>
      )
    case 'code-review':
      return (
        <svg {...common} stroke="currentColor">
          <path d="M8 4 3 12l5 8" />
          <path d="M16 4l5 8-5 8" />
        </svg>
      )
    case 'ai-cost':
      return (
        <svg {...common} stroke="currentColor">
          <path d="M12 2v2.5M12 19.5V22M4.5 12H2M22 12h-2.5" />
          <circle cx="12" cy="12" r="6.5" />
          <path d="M12 8.5v7M9.8 15h3.1a1.6 1.6 0 0 0 0-3.2h-1.8a1.6 1.6 0 0 1 0-3.2h3" />
        </svg>
      )
    case 'personal':
      return (
        <svg {...common} stroke="currentColor">
          <circle cx="12" cy="8" r="3.5" />
          <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
        </svg>
      )
    case 'admin':
      return (
        <svg {...common} stroke="currentColor">
          <path d="M12 2.5l7.5 3.3v5.2c0 4.7-3.2 8.9-7.5 10-4.3-1.1-7.5-5.3-7.5-10V5.8L12 2.5z" />
          <path d="M9.3 12l1.9 1.9 3.6-3.9" />
        </svg>
      )
    case 'setup':
      return (
        <svg {...common} stroke="currentColor">
          <path d="M9 3h6l1 2.5 2.5 1L21 6l1.5 3-2 2v2l2 2L21 18l-2.5-.5-1 2.5H9l-1-2.5-2.5-1L4 18l-1.5-3 2-2v-2l-2-2L4 6l2.5.5L9 4z" />
          <circle cx="12" cy="12" r="3" />
        </svg>
      )
  }
}

const NAV_LABELS: Record<View, string> = {
  cockpit: 'Cockpit',
  teams: 'Teams',
  investment: 'Investment Profile',
  'code-review': 'Code Review',
  'ai-cost': 'AI Cost Track',
  personal: 'Personal Activity',
  setup: 'Setup',
  admin: 'Admin',
}

const ROLE_STYLES: Record<string, string> = {
  ADMIN: 'bg-rose-50 text-rose-600 ring-rose-200',
  ENG_LEADER: 'bg-kpmg-50 text-kpmg-700 ring-kpmg-200',
  MANAGER: 'bg-cobalt-50 text-cobalt-700 ring-cobalt-200',
  IC: 'bg-emerald-50 text-emerald-600 ring-emerald-200',
  FINANCE_READONLY: 'bg-amber-50 text-amber-600 ring-amber-200',
}

function initialsFor(email: string) {
  const name = email.split('@')[0] ?? ''
  const parts = name.split(/[.\-_]/).filter(Boolean)
  const chars = parts.length > 1 ? parts[0][0] + parts[1][0] : name.slice(0, 2)
  return chars.toUpperCase()
}

type Stage = 'landing' | 'login' | 'app'

function AppShell({ session, onLogout }: { session: Session; onLogout: () => void }) {
  // Role-aware navigation (PRD §8) — previously every tab rendered for every role, including
  // Admin for a MANAGER (the backend already blocked their API calls with a 403, but the tab
  // itself had no business being visible at all).
  const allowedViews = TAB_ACCESS[session.role] ?? []
  const [view, setView] = useState<View>(() => defaultViewFor(session.role))
  const roleLabel = session.role.replace('_', ' ')
  const roleStyle = ROLE_STYLES[session.role] ?? 'bg-slate-100 text-slate-600 ring-slate-200'

  return (
    <div className="flex h-screen w-screen flex-col overflow-hidden bg-slate-50 text-slate-900">
      {/* KPMG masthead — the primary brand signal, spans the full width. */}
      <header className="flex h-14 shrink-0 items-center justify-between bg-[#00338D] px-5 text-white">
        <div className="flex items-center gap-3">
          <span className="flex h-8 shrink-0 items-center justify-center rounded bg-white px-2.5 text-xs font-extrabold tracking-tight text-[#00338D]">
            KPMG
          </span>
          <div className="hidden h-6 w-px bg-white/25 sm:block" />
          <div className="hidden sm:block">
            <div className="text-sm font-semibold leading-tight text-white">AI Impact Evaluation</div>
            <div className="text-[10px] text-blue-100">Engineering Intelligence &amp; Analytics Platform</div>
          </div>
        </div>
        <span className={`hidden items-center rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ring-1 ring-inset md:inline-flex ${roleStyle}`}>
          {roleLabel}
        </span>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <aside className="flex w-64 shrink-0 flex-col overflow-y-auto border-r border-slate-200 bg-white">
          <div className="flex items-center gap-2.5 border-b border-slate-100 p-4">
            <img
              src={aiImpactEvaluationLogo}
              alt="AI Impact Evaluation"
              className="h-7 w-7 rounded-lg object-cover ring-1 ring-slate-900/5"
            />
            <p className="text-[10px] font-semibold uppercase tracking-widest text-slate-400">Menu</p>
          </div>
          <nav className="flex-1 space-y-0.5 p-3">
            {allowedViews.map((id) => {
              const active = view === id
              return (
                <button
                  key={id}
                  onClick={() => setView(id)}
                  className={`flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-[13.5px] font-medium transition-colors ${
                    active
                      ? 'bg-[#00338D] text-white shadow-sm'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`}
                >
                  <span className={active ? 'text-white' : 'text-slate-400'}>
                    <NavIcon id={id} />
                  </span>
                  <span className="truncate">{NAV_LABELS[id]}</span>
                </button>
              )
            })}
          </nav>

          <div className="border-t border-slate-100 p-3">
            <div className="flex items-center gap-2.5 rounded-lg border border-slate-100 bg-slate-50 p-2.5">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[#00338D] text-[11px] font-semibold text-white">
                {initialsFor(session.email)}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-[12.5px] font-semibold text-slate-800">{session.email}</p>
                <span className={`mt-0.5 inline-flex items-center rounded-full px-1.5 py-0.5 text-[9.5px] font-semibold uppercase tracking-wide ring-1 ring-inset ${roleStyle}`}>
                  {roleLabel}
                </span>
              </div>
            </div>
            <button
              onClick={onLogout}
              className="mt-2 flex w-full items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white py-1.5 text-[12px] font-medium text-slate-500 transition-colors hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <path d="M16 17l5-5-5-5" />
                <path d="M21 12H9" />
              </svg>
              Log out
            </button>
          </div>
        </aside>

        <main className="flex-1 overflow-y-auto bg-slate-50 p-8">
          {!allowedViews.includes(view) ? (
            <p className="text-sm text-slate-400">Nothing here for your role yet.</p>
          ) : (
            <>
              {view === 'cockpit' && <Cockpit />}
              {view === 'teams' && <Teams />}
              {view === 'investment' && <InvestmentProfile />}
              {view === 'code-review' && <CodeReview />}
              {view === 'ai-cost' && <AiCostTrack />}
              {view === 'personal' && <Personal />}
              {view === 'setup' && <Setup />}
              {view === 'admin' && <Admin />}
            </>
          )}
        </main>
      </div>
    </div>
  )
}

export default function App() {
  const [stage, setStage] = useState<Stage>(() => (getSession() ? 'app' : 'landing'))
  const [session, setSession] = useState<Session | null>(() => getSession())

  if (stage === 'landing') {
    return <Landing onLogin={() => setStage('login')} />
  }

  if (stage === 'login' || !session) {
    return (
      <Login
        onBack={() => setStage('landing')}
        onSuccess={() => {
          setSession(getSession())
          setStage('app')
        }}
      />
    )
  }

  return (
    <AppShell
      session={session}
      onLogout={() => {
        logout()
        setSession(null)
        setStage('landing')
      }}
    />
  )
}