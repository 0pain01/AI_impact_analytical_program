import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import {
  fetchInvestmentProfile,
  fetchInvestmentProfileLinkedPrs,
  fetchTeams,
  type InvestmentProfileLinkedPrsPage,
  type InvestmentProfileResponse,
  type Team,
} from '../api'

const CATEGORY_COLORS: Record<string, string> = {
  Planned: '#0f172a',
  Unplanned: '#dc2626',
  Rework: '#d97706',
  Unclassifiable: '#94a3b8',
}
const CATEGORY_ORDER = ['Planned', 'Unplanned', 'Rework', 'Unclassifiable']
const DRILLDOWN_PAGE_SIZE = 20

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

  const [linkedPrs, setLinkedPrs] = useState<InvestmentProfileLinkedPrsPage | null>(null)
  const [linkedPrsError, setLinkedPrsError] = useState<string | null>(null)
  const [linkedPrsLoading, setLinkedPrsLoading] = useState(true)
  const [categoryFilter, setCategoryFilter] = useState('')
  const [drilldownPage, setDrilldownPage] = useState(0)

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

  // Resets to page 0 whenever scope or the category filter changes, same convention as
  // CodeReview/Jira's paginated tables.
  useEffect(() => {
    setDrilldownPage(0)
  }, [scope, categoryFilter])

  useEffect(() => {
    let cancelled = false
    setLinkedPrsLoading(true)
    setLinkedPrsError(null)
    fetchInvestmentProfileLinkedPrs({ days: 90, scope, category: categoryFilter || undefined, page: drilldownPage, pageSize: DRILLDOWN_PAGE_SIZE })
      .then((d) => {
        if (!cancelled) setLinkedPrs(d)
      })
      .catch((e: Error) => {
        if (!cancelled) setLinkedPrsError(e.message)
      })
      .finally(() => {
        if (!cancelled) setLinkedPrsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [scope, categoryFilter, drilldownPage])

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

      <div className="mt-6 rounded-lg border border-slate-200 bg-white p-4">
        <div className="mb-1 flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-medium text-slate-700">Verify PR ↔ Jira ticket matching</p>
            <p className="text-xs text-slate-400">
              Every PR/MR in scope, exactly what issue key its title matched, and what that resolved to — so you
              can confirm the automatic match is correct instead of trusting the regex blindly.
            </p>
          </div>
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="rounded-md border border-slate-200 px-2 py-1 text-sm"
          >
            <option value="">All categories</option>
            {CATEGORY_ORDER.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </div>

        {linkedPrsError && (
          <p className="py-6 text-center text-sm text-red-600">Could not load the drill-down: {linkedPrsError}</p>
        )}

        {!linkedPrsError && linkedPrsLoading && (
          <div className="mt-3 h-40 animate-pulse rounded-lg bg-slate-100" />
        )}

        {!linkedPrsError && !linkedPrsLoading && linkedPrs && (
          linkedPrs.items.length > 0 ? (
            <>
              <div className="mt-3 overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs text-slate-400">
                      <th className="pb-2 pr-4 uppercase tracking-wide">PR</th>
                      <th className="pb-2 pr-4 uppercase tracking-wide">Extracted key</th>
                      <th className="pb-2 pr-4 uppercase tracking-wide">Matched Jira issue</th>
                      <th className="pb-2 pr-4 uppercase tracking-wide">Category</th>
                    </tr>
                  </thead>
                  <tbody>
                    {linkedPrs.items.map((pr) => (
                      <tr key={`${pr.repo}/${pr.prId}`} className="border-b border-slate-100 last:border-0 align-top">
                        <td className="py-2 pr-4">
                          {pr.htmlUrl ? (
                            <a href={pr.htmlUrl} target="_blank" rel="noreferrer" className="font-medium text-blue-700 hover:underline">
                              {pr.repo}#{pr.number ?? pr.prId}
                            </a>
                          ) : (
                            <p className="font-medium">
                              {pr.repo}#{pr.number ?? pr.prId}
                            </p>
                          )}
                          <p className="text-xs text-slate-400">{pr.title}</p>
                        </td>
                        <td className="py-2 pr-4 text-slate-600">{pr.extractedIssueKey ?? '—'}</td>
                        <td className="py-2 pr-4">
                          {pr.jiraIssueKey ? (
                            <>
                              {pr.jiraUrl ? (
                                <a href={pr.jiraUrl} target="_blank" rel="noreferrer" className="font-medium text-blue-700 hover:underline">
                                  {pr.jiraIssueKey}
                                </a>
                              ) : (
                                <p className="font-medium text-slate-700">{pr.jiraIssueKey}</p>
                              )}
                              <p className="text-xs text-slate-400">{pr.jiraSummary}</p>
                            </>
                          ) : (
                            <span className="text-slate-400">No match</span>
                          )}
                        </td>
                        <td className="py-2">
                          <span
                            className="rounded px-2 py-0.5 text-xs font-medium text-white"
                            style={{ backgroundColor: CATEGORY_COLORS[pr.category] ?? '#94a3b8' }}
                          >
                            {pr.category}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
                <span>
                  {linkedPrs.totalCount} PR{linkedPrs.totalCount === 1 ? '' : 's'} in scope · page {drilldownPage + 1} of{' '}
                  {Math.max(1, Math.ceil(linkedPrs.totalCount / DRILLDOWN_PAGE_SIZE))}
                </span>
                <div className="flex gap-2">
                  <button
                    onClick={() => setDrilldownPage((p) => Math.max(0, p - 1))}
                    disabled={drilldownPage === 0}
                    className="rounded-md border border-slate-200 px-3 py-1 disabled:opacity-40"
                  >
                    Prev
                  </button>
                  <button
                    onClick={() =>
                      setDrilldownPage((p) =>
                        Math.min(Math.max(0, Math.ceil(linkedPrs.totalCount / DRILLDOWN_PAGE_SIZE) - 1), p + 1),
                      )
                    }
                    disabled={drilldownPage >= Math.ceil(linkedPrs.totalCount / DRILLDOWN_PAGE_SIZE) - 1}
                    className="rounded-md border border-slate-200 px-3 py-1 disabled:opacity-40"
                  >
                    Next
                  </button>
                </div>
              </div>
            </>
          ) : (
            <p className="py-6 text-center text-sm text-slate-400">No pull requests match this filter in this window.</p>
          )
        )}
      </div>
    </section>
  )
}