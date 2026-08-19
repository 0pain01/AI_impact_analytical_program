import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fetchInvestmentProfile, fetchTeams, type InvestmentProfileResponse, type Team } from '../api'

const CATEGORY_COLORS: Record<string, string> = {
  Planned: '#0f172a',
  Unplanned: '#dc2626',
  Rework: '#d97706',
  Unclassifiable: '#94a3b8',
}
const CATEGORY_ORDER = ['Planned', 'Unplanned', 'Rework', 'Unclassifiable']

/**
 * Investment Profile tab (PRD E5-S1). Scope is picked here, not passed in from a parent —
 * unlike Cockpit/Teams' org→team drill-down, this tab needs to reach a single repo too (e.g.
 * to check one specific project), so it offers both a team dropdown and a repo override.
 */
export default function InvestmentProfile() {
  const [data, setData] = useState<InvestmentProfileResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const [teams, setTeams] = useState<Team[]>([])
  const [teamId, setTeamId] = useState('')
  const [repoInput, setRepoInput] = useState('')
  const [repoScope, setRepoScope] = useState('')

  useEffect(() => {
    fetchTeams()
      .then(setTeams)
      .catch(() => {
        // Non-fatal — the team dropdown just won't have options; org-wide/repo-override still work.
      })
  }, [])

  // Repo override takes precedence over the team dropdown when set.
  const scope = repoScope || teamId || '*'

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchInvestmentProfile(90, scope)
      .then((d) => {
        if (!cancelled) setData(d)
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [scope])

  function applyRepoFilter(e: React.FormEvent) {
    e.preventDefault()
    setRepoScope(repoInput.trim())
  }

  function clearRepoFilter() {
    setRepoInput('')
    setRepoScope('')
  }

  const breakdown = [...(data?.breakdown ?? [])].sort(
    (a, b) => CATEGORY_ORDER.indexOf(a.category) - CATEGORY_ORDER.indexOf(b.category),
  )
  const totalPrs = breakdown.reduce((sum, s) => sum + s.count, 0)
  const unclassifiableShare = totalPrs > 0
    ? (breakdown.find((s) => s.category === 'Unclassifiable')?.count ?? 0) / totalPrs
    : 0

  return (
    <section>
      <div className="mb-1 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Investment Profile</h2>
        <div className="flex flex-wrap items-center gap-2">
          <select
            value={teamId}
            onChange={(e) => {
              setTeamId(e.target.value)
              setRepoInput('')
              setRepoScope('')
            }}
            disabled={!!repoScope}
            className="rounded-md border border-slate-200 px-2 py-1 text-sm disabled:opacity-50"
          >
            <option value="">Org-wide</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          <form onSubmit={applyRepoFilter} className="flex items-center gap-2">
            <input
              value={repoInput}
              onChange={(e) => setRepoInput(e.target.value)}
              placeholder="Or a specific repo…"
              className="rounded-md border border-slate-200 px-2 py-1 text-sm"
            />
            <button type="submit" className="rounded-md border border-slate-200 px-2 py-1 text-sm text-slate-600 hover:bg-slate-50">
              Go
            </button>
            {repoScope && (
              <button type="button" onClick={clearRepoFilter} className="text-sm text-slate-400 hover:text-slate-600">
                Clear
              </button>
            )}
          </form>
        </div>
      </div>
      <p className="mb-6 text-sm text-slate-500">
        {loading && 'Loading…'}
        {error && `Could not load investment profile: ${error}`}
        {!loading && !error && `${data?.windowLabel ?? 'Last 90 days'} · planned vs. unplanned engineering time`}
      </p>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          The metrics API is unreachable. Check that api-core is running, then reload.
        </div>
      )}

      {!error && loading && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-64 animate-pulse rounded-lg border border-slate-200 bg-white lg:col-span-1" />
          ))}
        </div>
      )}

      {!error && !loading && (
        <>
          {totalPrs === 0 ? (
            <p className="rounded-lg border border-slate-200 bg-white py-10 text-center text-sm text-slate-400">
              No pull requests in this window yet to classify.
            </p>
          ) : (
            <>
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-1">
                  <p className="text-sm text-slate-500">PRs by category</p>
                  <div className="h-64">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={breakdown}
                          dataKey="count"
                          nameKey="category"
                          innerRadius={50}
                          outerRadius={85}
                          paddingAngle={2}
                          isAnimationActive={false}
                        >
                          {breakdown.map((b) => (
                            <Cell key={b.category} fill={CATEGORY_COLORS[b.category] ?? '#94a3b8'} />
                          ))}
                        </Pie>
                        <Tooltip formatter={(v: number) => `${v} PRs`} />
                        <Legend verticalAlign="bottom" height={48} wrapperStyle={{ fontSize: 12 }} />
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                  <p className="mt-2 text-xs text-slate-400">
                    {(unclassifiableShare * 100).toFixed(0)}% of PRs had no matching Jira issue key in the title —
                    classified as Unclassifiable rather than guessed.
                  </p>
                </div>

                <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-2">
                  <p className="text-sm text-slate-500">Trend over time</p>
                  <div className="h-64">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={data?.trend ?? []} stackOffset="expand">
                        <CartesianGrid strokeDasharray="3 3" vertical={false} />
                        <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                        <YAxis tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} tick={{ fontSize: 12 }} />
                        <Tooltip />
                        <Legend wrapperStyle={{ fontSize: 12 }} />
                        <Bar dataKey="planned" stackId="a" name="Planned" fill={CATEGORY_COLORS.Planned} isAnimationActive={false} />
                        <Bar dataKey="unplanned" stackId="a" name="Unplanned" fill={CATEGORY_COLORS.Unplanned} isAnimationActive={false} />
                        <Bar dataKey="rework" stackId="a" name="Rework" fill={CATEGORY_COLORS.Rework} isAnimationActive={false} />
                        <Bar dataKey="unclassifiable" stackId="a" name="Unclassifiable" fill={CATEGORY_COLORS.Unclassifiable} isAnimationActive={false} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              </div>

              <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                <p className="mb-3 text-sm text-slate-500">Breakdown by team</p>
                {data && data.byTeam.length > 0 ? (
                  <div className="h-56">
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={data.byTeam} layout="vertical" stackOffset="expand" margin={{ left: 24 }}>
                        <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                        <XAxis type="number" tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} tick={{ fontSize: 12 }} />
                        <YAxis type="category" dataKey="team" tick={{ fontSize: 12 }} width={100} />
                        <Tooltip />
                        <Bar dataKey="planned" stackId="a" name="Planned" fill={CATEGORY_COLORS.Planned} isAnimationActive={false} />
                        <Bar dataKey="unplanned" stackId="a" name="Unplanned" fill={CATEGORY_COLORS.Unplanned} isAnimationActive={false} />
                        <Bar dataKey="rework" stackId="a" name="Rework" fill={CATEGORY_COLORS.Rework} isAnimationActive={false} />
                        <Bar dataKey="unclassifiable" stackId="a" name="Unclassifiable" fill={CATEGORY_COLORS.Unclassifiable} isAnimationActive={false} />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                ) : (
                  <p className="py-6 text-center text-sm text-slate-400">
                    No repos in this scope are mapped to a team yet (Admin console → Teams).
                  </p>
                )}
              </div>
            </>
          )}
        </>
      )}
    </section>
  )
}