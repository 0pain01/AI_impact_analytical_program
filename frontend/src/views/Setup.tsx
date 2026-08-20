import { useEffect, useState } from 'react'
import { fetchSetupStatus, type SetupChecklistItem, type SetupStatus } from '../api'

function ChecklistCard({ item }: { item: SetupChecklistItem }) {
  return (
    <div
      className={`relative overflow-hidden rounded-2xl border p-5 shadow-sm ${
        item.done ? 'border-emerald-100 bg-emerald-50/40 shadow-emerald-50' : 'border-slate-100 bg-white shadow-slate-100'
      }`}
    >
      <span
        className={`absolute inset-y-0 left-0 w-1.5 ${item.done ? 'bg-emerald-500' : 'bg-slate-200'}`}
      />
      <div className="flex items-start gap-3 pl-2">
        <span
          className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-bold ${
            item.done ? 'bg-emerald-500 text-white' : 'border-2 border-slate-200 text-transparent'
          }`}
        >
          ✓
        </span>
        <div>
          <p className="font-semibold text-slate-900">{item.label}</p>
          <p className="mt-1 text-xs text-slate-500">{item.detail}</p>
        </div>
      </div>
    </div>
  )
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

function TimeToValuePanel({ status }: { status: SetupStatus }) {
  const { minutesToValue, withinTarget, targetMinutes } = status

  if (minutesToValue === null) {
    return (
      <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm shadow-slate-100">
        <p className="text-sm font-medium text-slate-500">Time to value</p>
        <p className="mt-2 text-sm text-slate-400">
          Measured from the first Git/Jira event to the first populated dashboard, against a{' '}
          {targetMinutes}-minute target. Waiting on the checklist above to complete.
        </p>
      </div>
    )
  }

  const pct = Math.min(100, Math.round((minutesToValue / targetMinutes) * 100))

  return (
    <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm shadow-slate-100">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">Time to value</p>
        <span
          className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
            withinTarget ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
          }`}
        >
          {withinTarget ? 'Within target' : 'Over target'}
        </span>
      </div>
      <p className="mt-2 text-3xl font-bold tracking-tight text-slate-900">{formatMinutes(minutesToValue)}</p>
      <p className="mt-1 text-xs text-slate-400">Target: {targetMinutes} minutes, first connection → first populated dashboard</p>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100">
        <div
          className={`h-full rounded-full ${withinTarget ? 'bg-emerald-500' : 'bg-amber-500'}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="mt-3 border-t border-slate-100 pt-3 text-xs text-slate-400">
        This is a one-time pilot-onboarding record, not a live/recurring metric — it's fixed to the gap between the
        very first event this deployment ever ingested and the very first dashboard value it ever computed, both
        back at initial setup. Connecting more repos or teams later won't move this number; check the Sync status
        table on the Admin page for progress on anything you connect now.
      </p>
    </div>
  )
}

/** Guided setup checklist and time-to-value instrumentation (PRD E1-S5). */
export default function Setup() {
  const [status, setStatus] = useState<SetupStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    fetchSetupStatus()
      .then((s) => {
        if (!cancelled) setStatus(s)
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

  const remaining = status?.checklist.filter((i) => !i.done).length ?? 0

  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-kpmg-600 to-cobalt-600 text-sm text-white">
          ◆
        </span>
        <h2 className="text-xl font-bold tracking-tight text-slate-900">Setup</h2>
      </div>
      <p className="mb-6 text-sm text-slate-500">
        {loading && 'Loading setup status…'}
        {error && `Could not load setup status: ${error}`}
        {!loading && !error && status && remaining === 0 && 'All set — every connector is live.'}
        {!loading && !error && status && remaining > 0 && `${remaining} step${remaining === 1 ? '' : 's'} remaining.`}
      </p>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          The setup API is unreachable. Check that api-core is running, then reload.
        </div>
      )}
      {!error && status && (
        <>
          <div className="mb-6 grid grid-cols-2 gap-4">
            {status.checklist.map((item) => (
              <ChecklistCard key={item.key} item={item} />
            ))}
          </div>
          <TimeToValuePanel status={status} />
        </>
      )}
      {!error && loading && (
        <div className="grid grid-cols-2 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-20 animate-pulse rounded-2xl border border-slate-100 bg-white" />
          ))}
        </div>
      )}
    </section>
  )
}
