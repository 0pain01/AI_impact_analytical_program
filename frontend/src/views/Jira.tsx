import { useEffect, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { fetchJiraDashboard, type JiraDashboardResponse } from '../api'

type SortBy = 'age' | 'priority' | 'project'
type SortDir = 'asc' | 'desc'

const PAGE_SIZE = 20

const STATUS_COLORS: Record<string, string> = {
  new: '#94a3b8',
  indeterminate: '#2563eb',
  done: '#16a34a',
  unknown: '#cbd5e1',
}

function Kpi({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-lg font-semibold text-slate-900">{value}</p>
      <p className="text-xs text-slate-500">{label}</p>
      {hint && <p className="mt-1 text-[11px] text-slate-400">{hint}</p>}
    </div>
  )
}

function statusBadge(statusCategory: string | null) {
  const color = STATUS_COLORS[statusCategory ?? 'unknown'] ?? STATUS_COLORS.unknown
  return { backgroundColor: `${color}1a`, color }
}

function SortHeader({
  label,
  column,
  sortBy,
  sortDir,
  onClick,
}: {
  label: string
  column: SortBy
  sortBy: SortBy
  sortDir: SortDir
  onClick: (column: SortBy) => void
}) {
  const active = sortBy === column
  return (
    <th className="pb-2 pr-4">
      <button
        onClick={() => onClick(column)}
        className={`flex items-center gap-1 uppercase tracking-wide ${active ? 'text-slate-700' : 'text-slate-400'}`}
      >
        {label}
        {active && <span>{sortDir === 'asc' ? '↑' : '↓'}</span>}
      </button>
    </th>
  )
}

/** Jira Work Items dashboard (PRD PG — Jira-specific detail: items, topics, backlog health). */
export default function Jira() {
  const [data, setData] = useState<JiraDashboardResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const [days, setDays] = useState<30 | 90>(30)
  const [projectInput, setProjectInput] = useState('')
  const [project, setProject] = useState('*')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [sortBy, setSortBy] = useState<SortBy>('age')
  const [sortDir, setSortDir] = useState<SortDir>('desc')
  const [page, setPage] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchJiraDashboard({ days, project, q: search, sortBy, sortDir, page, pageSize: PAGE_SIZE })
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
  }, [days, project, search, sortBy, sortDir, page])

  function toggleSort(column: SortBy) {
    setPage(0)
    if (sortBy === column) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortBy(column)
      setSortDir('desc')
    }
  }

  function applyProjectFilter(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
    setProject(projectInput.trim() || '*')
  }

  function applySearch(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
  }

  const kpis = data?.kpis
  const statusBreakdown = data?.statusBreakdown ?? []
  const typeBreakdown = data?.typeBreakdown ?? []
  const priorityBreakdown = data?.priorityBreakdown ?? []
  const topAssignees = data?.topAssignees ?? []
  const topLabels = data?.topLabels ?? []
  const resolutionTrend = data?.resolutionTrend ?? []
  const issues = data?.issues
  const totalPages = issues ? Math.max(1, Math.ceil(issues.totalCount / PAGE_SIZE)) : 1
  const maxLabelCount = topLabels.reduce((max, l) => Math.max(max, l.count), 0)

  return (
    <section>
      <div className="mb-1 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Jira Work Items</h2>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex overflow-hidden rounded-md border border-slate-200 text-sm">
            {([30, 90] as const).map((d) => (
              <button
                key={d}
                onClick={() => {
                  setPage(0)
                  setDays(d)
                }}
                className={`px-3 py-1 ${days === d ? 'bg-slate-900 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}
              >
                {d} days
              </button>
            ))}
          </div>
          <form onSubmit={applyProjectFilter} className="flex items-center gap-2">
            <input
              value={projectInput}
              onChange={(e) => setProjectInput(e.target.value)}
              placeholder="Project key (e.g. ENG)…"
              className="w-40 rounded-md border border-slate-200 px-2 py-1 text-sm"
            />
            <button type="submit" className="rounded-md border border-slate-200 px-2 py-1 text-sm text-slate-600 hover:bg-slate-50">
              Go
            </button>
            {project !== '*' && (
              <button
                type="button"
                onClick={() => {
                  setProjectInput('')
                  setProject('*')
                  setPage(0)
                }}
                className="text-sm text-slate-400 hover:text-slate-600"
              >
                Clear
              </button>
            )}
          </form>
        </div>
      </div>
      <p className="mb-6 text-sm text-slate-500">
        {loading && 'Loading…'}
        {error && `Could not load Jira data: ${error}`}
        {!loading && !error && (data?.windowLabel ?? `Last ${days} days`)}
      </p>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          The metrics API is unreachable. Check that api-core is running and connector-jira has
          backfilled at least one project, then reload.
        </div>
      )}

      {!error && loading && !data && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-56 animate-pulse rounded-lg border border-slate-200 bg-white" />
          ))}
        </div>
      )}

      {!error && data && kpis && (
        <>
          <div className="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-5">
            <Kpi label="Open issues" value={String(kpis.openIssues)} hint="Current backlog — not windowed" />
            <Kpi label="Resolved" value={String(kpis.resolvedInWindow)} hint={`In the last ${days} days`} />
            <Kpi
              label="Median resolution"
              value={kpis.medianResolutionHoursP50 !== null ? `${kpis.medianResolutionHoursP50}h` : 'n/a'}
              hint={kpis.medianResolutionHoursP50 === null ? 'No issues resolved in this window yet' : 'Created → resolved, p50'}
            />
            <Kpi
              label="Reopen rate"
              value={kpis.reopenRate !== null ? `${kpis.reopenRate}%` : 'n/a'}
              hint="Of issues resolved in this window"
            />
            <Kpi
              label="Overdue"
              value={String(kpis.overdueCount)}
              hint="Open, past due date — not windowed"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm text-slate-500">Pipeline shape (To Do / In Progress / Done)</p>
              {statusBreakdown.length > 0 ? (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={statusBreakdown}
                        dataKey="count"
                        nameKey="label"
                        innerRadius={50}
                        outerRadius={85}
                        paddingAngle={2}
                        isAnimationActive={false}
                      >
                        {statusBreakdown.map((s) => (
                          <Cell key={s.statusCategory} fill={STATUS_COLORS[s.statusCategory] ?? STATUS_COLORS.unknown} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(v: number) => `${v} issues`} />
                      <Legend verticalAlign="bottom" height={36} wrapperStyle={{ fontSize: 12 }} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="flex h-56 items-center justify-center text-sm text-slate-400">
                  No issues created in this window yet.
                </p>
              )}
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm text-slate-500">Resolution time trend (median hours, weekly)</p>
              {resolutionTrend.length > 0 ? (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={resolutionTrend}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="weekStart" tick={{ fontSize: 11 }} />
                      <YAxis unit="h" tick={{ fontSize: 12 }} />
                      <Tooltip formatter={(v: number) => `${v}h`} />
                      <Line
                        type="monotone"
                        dataKey="medianResolutionHours"
                        name="Median resolution"
                        stroke="#0f172a"
                        strokeWidth={2}
                        dot={{ r: 3 }}
                        isAnimationActive={false}
                        connectNulls
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="flex h-56 items-center justify-center text-sm text-slate-400">
                  No issues resolved in this window yet.
                </p>
              )}
            </div>
          </div>

          <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm text-slate-500">Open backlog by type</p>
              {typeBreakdown.length > 0 ? (
                <div className="h-48">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={typeBreakdown} layout="vertical" margin={{ left: 8 }}>
                      <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
                      <YAxis type="category" dataKey="issueType" tick={{ fontSize: 11 }} width={90} />
                      <Tooltip />
                      <Bar dataKey="count" fill="#0f172a" radius={[0, 4, 4, 0]} isAnimationActive={false} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="flex h-48 items-center justify-center text-sm text-slate-400">No open issues.</p>
              )}
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm text-slate-500">Open backlog by priority</p>
              {priorityBreakdown.length > 0 ? (
                <div className="h-48">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={priorityBreakdown} layout="vertical" margin={{ left: 8 }}>
                      <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                      <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} />
                      <YAxis type="category" dataKey="priority" tick={{ fontSize: 11 }} width={90} />
                      <Tooltip />
                      <Bar dataKey="count" fill="#334155" radius={[0, 4, 4, 0]} isAnimationActive={false} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="flex h-48 items-center justify-center text-sm text-slate-400">No open issues.</p>
              )}
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm text-slate-500">Assignee workload (open issues)</p>
              {topAssignees.length > 0 ? (
                <div className="h-48">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={topAssignees}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="assignee" tick={{ fontSize: 10 }} interval={0} angle={-25} textAnchor="end" height={50} />
                      <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                      <Tooltip />
                      <Bar dataKey="openCount" name="Open issues" fill="#00338D" radius={[4, 4, 0, 0]} isAnimationActive={false} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="flex h-48 items-center justify-center text-sm text-slate-400">No open issues.</p>
              )}
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
            <p className="mb-3 text-sm text-slate-500">Topics (most common labels across the open backlog)</p>
            {topLabels.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {topLabels.map((l) => {
                  const weight = maxLabelCount > 0 ? l.count / maxLabelCount : 0
                  return (
                    <span
                      key={l.label}
                      className="inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm font-medium"
                      style={{
                        backgroundColor: `rgba(0, 51, 141, ${0.08 + weight * 0.17})`,
                        color: '#00338D',
                      }}
                    >
                      {l.label}
                      <span className="text-xs font-semibold text-[#00338D]/70">{l.count}</span>
                    </span>
                  )
                })}
              </div>
            ) : (
              <p className="py-4 text-center text-sm text-slate-400">No labels on any open issue in scope.</p>
            )}
          </div>

          <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-slate-500">Open issues (flag before they slip)</p>
              <form onSubmit={applySearch} className="flex items-center gap-2">
                <input
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  placeholder="Search key or summary…"
                  className="rounded-md border border-slate-200 px-2 py-1 text-sm"
                />
                <button type="submit" className="rounded-md border border-slate-200 px-2 py-1 text-sm text-slate-600 hover:bg-slate-50">
                  Search
                </button>
                {search && (
                  <button
                    type="button"
                    onClick={() => {
                      setSearchInput('')
                      setSearch('')
                      setPage(0)
                    }}
                    className="text-sm text-slate-400 hover:text-slate-600"
                  >
                    Clear
                  </button>
                )}
              </form>
            </div>

            {issues && issues.items.length > 0 ? (
              <>
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs text-slate-400">
                      <th className="pb-2 pr-4 uppercase tracking-wide">Issue</th>
                      <SortHeader label="Project" column="project" sortBy={sortBy} sortDir={sortDir} onClick={toggleSort} />
                      <th className="pb-2 pr-4 uppercase tracking-wide">Type</th>
                      <th className="pb-2 pr-4 uppercase tracking-wide">Status</th>
                      <SortHeader label="Priority" column="priority" sortBy={sortBy} sortDir={sortDir} onClick={toggleSort} />
                      <th className="pb-2 pr-4 uppercase tracking-wide">Assignee</th>
                      <th className="pb-2 pr-4 uppercase tracking-wide">Due</th>
                      <SortHeader label="Age" column="age" sortBy={sortBy} sortDir={sortDir} onClick={toggleSort} />
                    </tr>
                  </thead>
                  <tbody>
                    {issues.items.map((issue) => (
                      <tr key={issue.issueKey} className="border-b border-slate-100 last:border-0">
                        <td className="py-2 pr-4">
                          <p className="font-medium">{issue.issueKey}</p>
                          <p className="max-w-xs truncate text-xs text-slate-400">{issue.summary}</p>
                        </td>
                        <td className="py-2 pr-4 text-slate-600">{issue.projectKey}</td>
                        <td className="py-2 pr-4 text-slate-600">{issue.issueType ?? '—'}</td>
                        <td className="py-2 pr-4">
                          <span
                            className="rounded px-2 py-0.5 text-xs font-medium"
                            style={statusBadge(issue.statusCategory)}
                          >
                            {issue.status ?? 'Unknown'}
                          </span>
                        </td>
                        <td className="py-2 pr-4 text-slate-600">{issue.priority ?? '—'}</td>
                        <td className="py-2 pr-4 text-slate-600">{issue.assignee ?? 'Unassigned'}</td>
                        <td className="py-2 pr-4">
                          {issue.dueDate ? (
                            <span className={issue.overdue ? 'font-medium text-red-600' : 'text-slate-600'}>
                              {issue.dueDate}
                              {issue.overdue && ' (overdue)'}
                            </span>
                          ) : (
                            <span className="text-slate-400">—</span>
                          )}
                        </td>
                        <td className="py-2 text-slate-600">{issue.ageDays}d</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
                  <span>
                    {issues.totalCount} open issue{issues.totalCount === 1 ? '' : 's'} in scope · page {page + 1} of{' '}
                    {totalPages}
                  </span>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      disabled={page === 0}
                      className="rounded-md border border-slate-200 px-3 py-1 disabled:opacity-40"
                    >
                      Prev
                    </button>
                    <button
                      onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                      disabled={page >= totalPages - 1}
                      className="rounded-md border border-slate-200 px-3 py-1 disabled:opacity-40"
                    >
                      Next
                    </button>
                  </div>
                </div>
              </>
            ) : (
              <p className="py-6 text-center text-sm text-slate-400">
                {search ? `No open issues matching "${search}" in scope.` : 'No open issues in scope right now.'}
              </p>
            )}
          </div>
        </>
      )}
    </section>
  )
}
