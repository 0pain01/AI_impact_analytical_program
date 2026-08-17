import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fetchCodeReview, type CodeReviewResponse } from '../api'

type SortBy = 'age' | 'repo'
type SortDir = 'asc' | 'desc'

const PAGE_SIZE = 20

function ageBadge(hours: number) {
  if (hours >= 72) return 'bg-red-100 text-red-700'
  if (hours >= 48) return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

function SortHeader({
  label,
  active,
  dir,
  onClick,
}: {
  label: string
  active: boolean
  dir: SortDir
  onClick: () => void
}) {
  return (
    <th className="pb-2 pr-4">
      <button
        onClick={onClick}
        className={`flex items-center gap-1 uppercase tracking-wide ${active ? 'text-slate-700' : 'text-slate-400'}`}
      >
        {label}
        {active && <span>{dir === 'asc' ? '↑' : '↓'}</span>}
      </button>
    </th>
  )
}

/** Code Review tab (PRD PG-4): scope is a repo, "*" for org, or a team id — same convention as Cockpit. */
export default function CodeReview({ scope = '*' }: { scope?: string }) {
  const [data, setData] = useState<CodeReviewResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const [days, setDays] = useState<30 | 90>(30)
  const [repoInput, setRepoInput] = useState('')
  const [repoFilter, setRepoFilter] = useState('')
  const [sortBy, setSortBy] = useState<SortBy>('age')
  const [sortDir, setSortDir] = useState<SortDir>('desc')
  const [page, setPage] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchCodeReview({ days, scope, repo: repoFilter, sortBy, sortDir, page, pageSize: PAGE_SIZE })
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
  }, [days, scope, repoFilter, sortBy, sortDir, page])

  function toggleSort(column: SortBy) {
    setPage(0)
    if (sortBy === column) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortBy(column)
      setSortDir('desc')
    }
  }

  function applyRepoFilter(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
    setRepoFilter(repoInput.trim())
  }

  const cycleStages = data?.cycleStages ?? []
  const reviewLoad = data?.reviewLoad ?? []
  const agingPrs = data?.agingPrs
  const hasCycleData = cycleStages.some((s) => s.hoursP50 !== null)
  const totalPages = agingPrs ? Math.max(1, Math.ceil(agingPrs.totalCount / PAGE_SIZE)) : 1

  return (
    <section>
      <div className="mb-1 flex items-center justify-between gap-4">
        <h2 className="text-xl font-semibold">Code Review Analytics</h2>
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
      </div>
      <p className="mb-6 text-sm text-slate-500">
        {loading && 'Loading…'}
        {error && `Could not load code review data: ${error}`}
        {!loading && !error && (data?.windowLabel ?? `Last ${days} days`)}
      </p>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          The metrics API is unreachable. Check that api-core is running, then reload.
        </div>
      )}

      {!error && loading && !data && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {[1, 2].map((i) => (
            <div key={i} className="h-64 animate-pulse rounded-lg border border-slate-200 bg-white" />
          ))}
        </div>
      )}

      {!error && data && (
        <>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm text-slate-500">PR cycle time breakdown (p50 hours)</p>
              {hasCycleData ? (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={cycleStages} layout="vertical" margin={{ left: 24 }}>
                      <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                      <XAxis type="number" unit="h" tick={{ fontSize: 12 }} />
                      <YAxis type="category" dataKey="stage" tick={{ fontSize: 11 }} width={140} />
                      <Tooltip formatter={(v: number) => `${v}h`} />
                      <Bar dataKey="hoursP50" fill="#0f172a" radius={[0, 4, 4, 0]} isAnimationActive={false} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="flex h-56 items-center justify-center text-sm text-slate-400">
                  Not enough PRs with both a review and a merge in this window yet.
                </p>
              )}
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <p className="mb-3 text-sm text-slate-500">Review load by reviewer</p>
              {reviewLoad.length > 0 ? (
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={reviewLoad}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="reviewer" tick={{ fontSize: 11 }} />
                      <YAxis tick={{ fontSize: 12 }} />
                      <Tooltip />
                      <Bar dataKey="reviews" name="Reviews completed" fill="#334155" radius={[4, 4, 0, 0]} isAnimationActive={false} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <p className="flex h-56 items-center justify-center text-sm text-slate-400">
                  No reviews recorded in this window yet.
                </p>
              )}
            </div>
          </div>

          <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-slate-500">Aging pull requests (flag before they block release)</p>
              <form onSubmit={applyRepoFilter} className="flex items-center gap-2">
                <input
                  value={repoInput}
                  onChange={(e) => setRepoInput(e.target.value)}
                  placeholder="Filter by repo…"
                  className="rounded-md border border-slate-200 px-2 py-1 text-sm"
                />
                <button type="submit" className="rounded-md border border-slate-200 px-2 py-1 text-sm text-slate-600 hover:bg-slate-50">
                  Filter
                </button>
                {repoFilter && (
                  <button
                    type="button"
                    onClick={() => {
                      setRepoInput('')
                      setRepoFilter('')
                      setPage(0)
                    }}
                    className="text-sm text-slate-400 hover:text-slate-600"
                  >
                    Clear
                  </button>
                )}
              </form>
            </div>

            {agingPrs && agingPrs.items.length > 0 ? (
              <>
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-xs text-slate-400">
                      <th className="pb-2 pr-4 uppercase tracking-wide">PR</th>
                      <SortHeader label="Repo" active={sortBy === 'repo'} dir={sortDir} onClick={() => toggleSort('repo')} />
                      <th className="pb-2 pr-4 uppercase tracking-wide">Author</th>
                      <th className="pb-2 pr-4 uppercase tracking-wide">Requested reviewers</th>
                      <th className="pb-2 pr-4 uppercase tracking-wide" title="Not yet available — see note below">
                        Size
                      </th>
                      <SortHeader label="Age" active={sortBy === 'age'} dir={sortDir} onClick={() => toggleSort('age')} />
                    </tr>
                  </thead>
                  <tbody>
                    {agingPrs.items.map((pr) => (
                      <tr key={pr.id} className="border-b border-slate-100 last:border-0">
                        <td className="py-2 pr-4">
                          <p className="font-medium">{pr.id}</p>
                          <p className="text-xs text-slate-400">{pr.title}</p>
                        </td>
                        <td className="py-2 pr-4 text-slate-600">{pr.repo}</td>
                        <td className="py-2 pr-4 text-slate-600">{pr.author}</td>
                        <td className="py-2 pr-4 text-slate-600">
                          {pr.reviewers.length > 0 ? pr.reviewers.join(', ') : '—'}
                        </td>
                        <td className="py-2 pr-4 text-slate-400">
                          {pr.sizeLines !== null ? `${pr.sizeLines} lines` : 'n/a'}
                        </td>
                        <td className="py-2">
                          <span className={`rounded px-2 py-0.5 text-xs font-medium ${ageBadge(pr.ageHours)}`}>{pr.ageHours}h</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <div className="mt-4 flex items-center justify-between text-sm text-slate-500">
                  <span>
                    {agingPrs.totalCount} open PR{agingPrs.totalCount === 1 ? '' : 's'} in scope · page {page + 1} of {totalPages}
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
                {repoFilter ? `No open PRs matching "${repoFilter}" in this window.` : 'No open pull requests in scope right now.'}
              </p>
            )}
          </div>
        </>
      )}
    </section>
  )
}