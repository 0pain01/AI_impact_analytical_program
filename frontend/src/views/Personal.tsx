import { useEffect, useState } from 'react'
import { fetchPersonalActivity, type PersonalActivity } from '../api'

function ageBadge(hours: number) {
  if (hours >= 72) return 'bg-red-100 text-red-700'
  if (hours >= 48) return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

function reviewStateBadge(state: string) {
  if (state === 'APPROVED') return 'bg-emerald-100 text-emerald-700'
  if (state === 'CHANGES_REQUESTED') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

/** Personal Activity tab (IC role, PRD persona table: "Self only"). Non-punitive by design —
 * this is your own trend, not a leaderboard, so there's nothing here comparing you to anyone. */
export default function Personal() {
  const [data, setData] = useState<PersonalActivity | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    fetchPersonalActivity()
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
  }, [])

  return (
    <section>
      <h2 className="mb-1 text-xl font-semibold">Personal Activity</h2>
      <p className="mb-6 text-sm text-slate-500">Your own open PRs and reviews — visible only to you.</p>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Could not load your activity: {error}
        </div>
      )}

      {!error && loading && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          {[1, 2].map((i) => (
            <div key={i} className="h-56 animate-pulse rounded-lg border border-slate-200 bg-white" />
          ))}
        </div>
      )}

      {!error && !loading && data && !data.githubLogin && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          Your account isn't linked to a GitHub username yet, so there's nothing to show. Ask an admin to set
          it in the Admin console's Users panel.
        </div>
      )}

      {!error && !loading && data && data.githubLogin && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="mb-3 text-sm text-slate-500">Your open pull requests</p>
            {data.openPrs.length > 0 ? (
              <ul className="space-y-2">
                {data.openPrs.map((pr) => (
                  <li key={pr.id} className="flex items-center justify-between border-b border-slate-100 pb-2 text-sm last:border-0">
                    <div className="min-w-0 pr-3">
                      <p className="font-medium">
                        {pr.id} <span className="text-slate-400">· {pr.repo}</span>
                      </p>
                      <p className="truncate text-xs text-slate-400">{pr.title}</p>
                    </div>
                    <span className={`shrink-0 rounded px-2 py-0.5 text-xs font-medium ${ageBadge(pr.ageHours)}`}>{pr.ageHours}h</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="py-6 text-center text-sm text-slate-400">No open PRs right now — nice and clear.</p>
            )}
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="mb-3 text-sm text-slate-500">Reviews you've given recently</p>
            {data.recentReviewsGiven.length > 0 ? (
              <ul className="space-y-2">
                {data.recentReviewsGiven.map((r, i) => (
                  <li key={`${r.repo}-${r.prId}-${i}`} className="flex items-center justify-between border-b border-slate-100 pb-2 text-sm last:border-0">
                    <p className="font-medium">
                      {r.prId} <span className="text-slate-400">· {r.repo}</span>
                    </p>
                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${reviewStateBadge(r.state)}`}>{r.state}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="py-6 text-center text-sm text-slate-400">No reviews recorded yet.</p>
            )}
          </div>
        </div>
      )}
    </section>
  )
}