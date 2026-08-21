import { useEffect, useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { fetchAiCostTrack, type AiCostTrackResponse } from '../api'

const TOOL_COLORS: Record<string, string> = {
  'Claude Code': '#dc2f7e',
  'GitHub Copilot': '#6366f1',
  Cursor: '#14b8a6',
}

const TABS = ['Spend', 'Adoption', 'Impact', 'ROI'] as const
type Tab = (typeof TABS)[number]

function currency(v: number) {
  return `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`
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

function NotAvailableYet({ reason }: { reason: string }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 p-6 text-center">
      <p className="text-sm font-medium text-slate-600">Not available yet</p>
      <p className="mx-auto mt-1 max-w-md text-xs text-slate-400">{reason}</p>
    </div>
  )
}

export default function AiCostTrack() {
  const [tab, setTab] = useState<Tab>('Spend')
  const [data, setData] = useState<AiCostTrackResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchAiCostTrack(30)
      .then((d) => {
        if (!cancelled) setData(d)
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (error) {
    return (
      <section>
        <h2 className="mb-4 text-xl font-semibold">AI Cost Track</h2>
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          Could not load AI Cost Track: {error}
        </div>
      </section>
    )
  }

  if (!data) {
    return (
      <section>
        <h2 className="mb-4 text-xl font-semibold">AI Cost Track</h2>
        <p className="text-sm text-slate-400">Loading…</p>
      </section>
    )
  }

  const { windowLabel, kpis, spendByTool, spendTrend, dailySpend, developerAllocation, assumptions } = data
  const totalSpend = spendByTool.reduce((sum, s) => sum + s.monthlySpend, 0)
  const costPerPrTrend = spendTrend.map((p) => ({
    month: p.month,
    costPerPr: p.prsAssisted > 0 ? p.spend / p.prsAssisted : 0,
  }))

  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <h2 className="text-xl font-semibold">AI Cost Track</h2>
        <span
          className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800"
          title="Every figure below is computed by the real query/formula against sample input data — the two usage-report files and PR history are shaped exactly like the real Claude Code / GitHub Copilot / GitHub APIs, so pointing the connector at a genuine enterprise export instead requires no changes downstream."
        >
          Demo data · real API schema
        </span>
      </div>
      <p className="mb-6 text-sm text-slate-500">
        {windowLabel} · AI coding-assistant spend, adoption, impact &amp; ROI (E9) · figures are computed live from
        sample usage/PR data, not a genuine enterprise export yet
      </p>

      <div className="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Kpi label="Total AI spend" value={currency(kpis.totalSpend)} />
        <Kpi label="Average cost / PR" value={`$${kpis.costPerPr.toFixed(2)}`} hint="Claude Code only — Copilot's usage export doesn't track PRs" />
        <Kpi label="Cost / active dev-day" value={`$${kpis.costPerDevDay.toFixed(2)}`} />
        <Kpi
          label="Org adoption rate"
          value={`${(kpis.adoptionRate * 100).toFixed(0)}%`}
          hint={`vs. ${assumptions.licensedSeatsAssumed} assumed licensed seats`}
        />
      </div>

      <div className="mb-4 inline-flex rounded-lg border border-slate-200 bg-white p-1">
        {TABS.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`rounded-md px-3.5 py-1.5 text-sm font-medium transition-colors ${
              tab === t ? 'bg-slate-900 text-white' : 'text-slate-500 hover:text-slate-800'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'Spend' && (
        <div className="space-y-4">
          {/* Cost per merged PR */}
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-1">
              <h3 className="text-sm font-semibold text-slate-900">Cost per PR</h3>
              <p className="mb-4 text-xs text-slate-500">
                Per-day Claude Code cost divided by PRs it was involved in — the unit-economics view procurement
                and Finance ask for, computed from the sample usage data below via the real formula.
              </p>
              <p className="text-3xl font-semibold text-slate-900">${kpis.costPerPr.toFixed(2)}</p>
              <p className="text-xs text-slate-400">org-wide average, {windowLabel.toLowerCase()}</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-2">
              <p className="mb-2 text-sm text-slate-500">Cost per PR — trend</p>
              <div className="h-48">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={costPerPrTrend}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                    <YAxis tickFormatter={(v) => `$${v.toFixed(0)}`} tick={{ fontSize: 12 }} />
                    <Tooltip formatter={(v: number) => `$${v.toFixed(2)}`} />
                    <Line type="monotone" dataKey="costPerPr" name="Cost / PR" stroke="#0f172a" strokeWidth={2} dot={false} isAnimationActive={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Spend over time */}
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <h3 className="text-sm font-semibold text-slate-900">Spend over time</h3>
            <p className="mb-3 text-xs text-slate-500">
              Daily cost across every connected AI tool. Claude Code's cost is usage-based billing from its usage
              report (sample data, real formula); Copilot has no per-request cost, so its figure is a configured
              per-seat price allocated only across days it was actually used.
            </p>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={dailySpend}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} interval={3} />
                  <YAxis tickFormatter={(v) => `$${v}`} tick={{ fontSize: 12 }} />
                  <Tooltip formatter={(v: number) => `$${v.toFixed(2)}`} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Line type="monotone" dataKey="claudeCode" name="Claude Code" stroke={TOOL_COLORS['Claude Code']} strokeWidth={2} dot={false} isAnimationActive={false} />
                  <Line type="monotone" dataKey="githubCopilot" name="GitHub Copilot" stroke={TOOL_COLORS['GitHub Copilot']} strokeWidth={2} dot={false} isAnimationActive={false} />
                  <Line type="monotone" dataKey="cursor" name="Cursor" stroke={TOOL_COLORS.Cursor} strokeWidth={2} dot={false} isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            {dailySpend.length === 0 && (
              <p className="mt-2 text-xs text-slate-400">No AI usage data ingested for this window yet.</p>
            )}
            <div className="mt-3 flex flex-wrap gap-4 border-t border-slate-100 pt-3 text-xs text-slate-500">
              {spendByTool.map((s) => (
                <span key={s.tool} className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full" style={{ backgroundColor: TOOL_COLORS[s.tool] ?? '#94a3b8' }} />
                  {s.tool}: <span className="font-medium text-slate-800">{currency(s.monthlySpend)}</span>
                  {totalSpend > 0 && (
                    <span className="text-slate-400">({((s.monthlySpend / totalSpend) * 100).toFixed(0)}%)</span>
                  )}
                </span>
              ))}
              {spendByTool.length === 0 && <span className="text-slate-400">No spend recorded yet.</span>}
            </div>
          </div>

          {/* Developer-level allocation */}
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <h3 className="text-sm font-semibold text-slate-900">Developer-level allocation</h3>
            <p className="mb-3 text-xs text-slate-500">
              Per-developer spend and PR involvement for the window, computed from sample usage data. Team-level
              rollup isn't available yet —
              AI-telemetry actor keys (Claude Code email / Copilot login) aren't identity-resolved to
              <code className="mx-1 rounded bg-slate-100 px-1">core.team</code> the way Git/Jira contributors are.
            </p>
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400">
                  <th className="py-2 pr-4 font-medium">Developer</th>
                  <th className="py-2 pr-4 font-medium">Tool</th>
                  <th className="py-2 pr-4 font-medium">Spend</th>
                  <th className="py-2 font-medium">PRs</th>
                </tr>
              </thead>
              <tbody>
                {developerAllocation.map((d) => (
                  <tr key={`${d.tool}-${d.developer}`} className="border-b border-slate-100 last:border-0">
                    <td className="py-2 pr-4 font-medium text-slate-900">{d.developer}</td>
                    <td className="py-2 pr-4 text-slate-600">
                      <span className="flex items-center gap-1.5">
                        <span className="h-2 w-2 rounded-full" style={{ backgroundColor: TOOL_COLORS[d.tool] ?? '#94a3b8' }} />
                        {d.tool}
                      </span>
                    </td>
                    <td className="py-2 pr-4 text-slate-700">{currency(d.spend)}</td>
                    <td className="py-2 text-slate-700">{d.mergedPrs || '—'}</td>
                  </tr>
                ))}
                {developerAllocation.length === 0 && (
                  <tr>
                    <td colSpan={4} className="py-4 text-center text-sm text-slate-400">
                      No developer usage data ingested for this window yet.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'Adoption' && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-1 text-sm text-slate-500">Org-wide adoption rate</p>
          <p className="text-3xl font-semibold text-slate-900">{(kpis.adoptionRate * 100).toFixed(0)}%</p>
          <p className="mt-1 text-xs text-slate-400">
            {developerAllocation.length > 0 ? new Set(developerAllocation.map((d) => d.developer)).size : 0} distinct
            active AI-tool user(s) ÷ {assumptions.licensedSeatsAssumed} assumed licensed seats — an org-configurable
            assumption, not a real headcount lookup (see below).
          </p>
          <div className="mt-4">
            <NotAvailableYet reason="Team-level adoption breakdown needs contributor→team identity resolution for AI-telemetry actor keys, which doesn't exist yet — see the Developer-level allocation table on the Spend tab for what is real today." />
          </div>
          <p className="mt-3 text-xs text-slate-400">{assumptions.methodologyNote}</p>
        </div>
      )}

      {tab === 'Impact' && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-1 text-sm text-slate-500">AI-assisted vs. non-AI PR cycle time</p>
          {data.impact ? (
            <>
              <p className="mb-4 text-xs text-slate-400">
                Median hours from PR open to merge, segmented by whether the PR carries a known AI co-author
                trailer (Claude Code / Copilot conventions) — {(data.impact.aiAssistedShare * 100).toFixed(0)}% of
                merged PRs in this window were AI-assisted ({data.impact.aiAssistedPrCount} of{' '}
                {data.impact.aiAssistedPrCount + data.impact.nonAiPrCount}).
              </p>
              {data.impact.aiAssistedPrCount < 10 && (
                <p className="mb-4 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  Small sample — only {data.impact.aiAssistedPrCount} AI-assisted PRs in this window. Treat the
                  median as directional, not a confident estimate; it will stabilize as more AI-attributed PRs merge.
                </p>
              )}
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                <div className="lg:col-span-2 h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={[
                        { bucket: 'AI-assisted', hours: data.impact.aiAssistedMedianCycleHours, prs: data.impact.aiAssistedPrCount },
                        { bucket: 'Non-AI', hours: data.impact.nonAiMedianCycleHours, prs: data.impact.nonAiPrCount },
                      ]}
                    >
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="bucket" tick={{ fontSize: 12 }} />
                      <YAxis tickFormatter={(v) => `${v}h`} tick={{ fontSize: 12 }} />
                      <Tooltip formatter={(v: number, name) => (name === 'hours' ? [`${v.toFixed(1)}h median`, 'Cycle time'] : v)} />
                      <Bar dataKey="hours" name="hours" isAnimationActive={false} radius={[4, 4, 0, 0]}>
                        <Cell fill="#dc2f7e" />
                        <Cell fill="#94a3b8" />
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
                <div className="flex flex-col justify-center gap-3">
                  <Kpi label="AI-assisted median cycle time" value={`${data.impact.aiAssistedMedianCycleHours.toFixed(1)}h`} hint={`${data.impact.aiAssistedPrCount} merged PRs`} />
                  <Kpi label="Non-AI median cycle time" value={`${data.impact.nonAiMedianCycleHours.toFixed(1)}h`} hint={`${data.impact.nonAiPrCount} merged PRs`} />
                </div>
              </div>
              <p className="mt-4 border-t border-slate-100 pt-3 text-xs text-slate-400">
                AI-attribution is trailer-based — PRs an AI tool helped with but that carry no trailer are counted
                as non-AI, which undercounts AI involvement. Change-failure-rate and rework-rate segmentation
                (also part of AI-04's full definition) aren't computed yet — deploy↔PR linkage doesn't exist in
                the schema.
              </p>
            </>
          ) : (
            <NotAvailableYet reason="Need at least 3 merged PRs in both the AI-assisted and non-AI-assisted buckets within the window to report a meaningful median — widen the window or wait for more PR history." />
          )}
        </div>
      )}

      {tab === 'ROI' && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-1 text-sm text-slate-500">Estimated value recovered vs. AI spend</p>
          {data.roi ? (
            <>
              <p className="mb-4 text-xs text-slate-400">
                estimatedHoursSaved = aiAssistedPrCount × (nonAi − aiAssisted median cycle hours); dollarValueRecovered
                = estimatedHoursSaved × blendedHourlyRate; roiMultiple = dollarValueRecovered / totalSpend.
              </p>
              {data.impact && data.impact.aiAssistedPrCount < 10 && (
                <p className="mb-4 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  Derived from the Impact tab's {data.impact.aiAssistedPrCount}-PR sample — directional, not a
                  confident estimate yet.
                </p>
              )}
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <Kpi label="Est. hours saved" value={`${data.roi.estimatedHoursSaved.toFixed(1)}h`} />
                <Kpi label="Value recovered" value={currency(data.roi.dollarValueRecovered)} />
                <Kpi
                  label="ROI multiple"
                  value={`${data.roi.roiMultiple.toFixed(1)}×`}
                  hint={data.roi.roiMultiple < 0 ? 'Negative — AI-assisted PRs were slower in this window' : undefined}
                />
                <Kpi label="Blended hourly rate" value={`$${data.roi.blendedHourlyRateUsd.toFixed(0)}/hr`} hint="Org-configurable assumption" />
              </div>
              <p className="mt-4 border-t border-slate-100 pt-3 text-xs text-slate-400">
                Can go negative — a negative cycle-time delta (AI-assisted PRs took longer) is reported as-is, never
                floored at zero. Same caveats as the Impact tab apply to the underlying cycle-time delta.
              </p>
            </>
          ) : (
            <NotAvailableYet reason="ROI is derived from the Impact tab's cycle-time delta — not available for the same reason (too few merged PRs in one or both buckets), or there's no AI spend recorded yet to divide into." />
          )}
        </div>
      )}
    </section>
  )
}
