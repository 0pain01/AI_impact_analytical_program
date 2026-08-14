import { useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { aiCostTrack } from '../mock/mockData'

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

type SpendScope = 'org' | 'team' | 'developer'

export default function AiCostTrack() {
  const [tab, setTab] = useState<Tab>('Spend')
  const [spendScope, setSpendScope] = useState<SpendScope>('org')
  const [selectedTeam, setSelectedTeam] = useState(aiCostTrack.teamAllocation[0].team)
  const [selectedDeveloper, setSelectedDeveloper] = useState(aiCostTrack.developerAllocation[0].developer)
  const { windowLabel, kpis, spendByTool, spendTrend, dailySpend, teamAllocation, developerAllocation, adoptionByTeam, impact, roi } =
    aiCostTrack
  const totalSpend = spendByTool.reduce((sum, s) => sum + s.monthlySpend, 0)

  const scopedKpis = (() => {
    if (spendScope === 'team') {
      const t = teamAllocation.find((x) => x.team === selectedTeam)!
      return {
        totalSpend: t.spend,
        costPerPr: t.spend / t.mergedPrs,
        costPerDevDay: t.spend / (t.developerCount * 30),
      }
    }
    if (spendScope === 'developer') {
      const d = developerAllocation.find((x) => x.developer === selectedDeveloper)!
      return { totalSpend: d.spend, costPerPr: d.spend / d.mergedPrs, costPerDevDay: d.spend / 30 }
    }
    return kpis
  })()

  const costPerPrTrend = spendTrend.map((p) => ({ month: p.month, costPerPr: p.spend / p.prsAssisted }))

  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <h2 className="text-xl font-semibold">AI Cost Track</h2>
        <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">Demo data</span>
      </div>
      <p className="mb-6 text-sm text-slate-500">{windowLabel} · AI coding-assistant spend, adoption &amp; ROI (E9)</p>

      <div className="mb-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Kpi label="Total AI spend" value={currency(kpis.totalSpend)} />
        <Kpi label="Average cost / PR" value={`$${kpis.costPerPr.toFixed(2)}`} />
        <Kpi label="Cost / dev / day" value={`$${kpis.costPerDevDay.toFixed(2)}`} />
        <Kpi label="Org adoption rate" value={`${(kpis.adoptionRate * 100).toFixed(0)}%`} />
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
          {/* Total AI spend */}
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
              <div>
                <h3 className="text-sm font-semibold text-slate-900">Total AI spend</h3>
                <p className="text-xs text-slate-500">
                  See your full AI budget in one number — the denominator every AI ROI conversation needs.
                </p>
              </div>
              <div className="flex items-center gap-2">
                <div className="inline-flex rounded-md border border-slate-200 bg-slate-50 p-0.5 text-xs">
                  {(['org', 'team', 'developer'] as SpendScope[]).map((s) => (
                    <button
                      key={s}
                      onClick={() => setSpendScope(s)}
                      className={`rounded px-2.5 py-1 font-medium capitalize transition-colors ${
                        spendScope === s ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-800'
                      }`}
                    >
                      {s}
                    </button>
                  ))}
                </div>
                {spendScope === 'team' && (
                  <select
                    value={selectedTeam}
                    onChange={(e) => setSelectedTeam(e.target.value)}
                    className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700"
                  >
                    {teamAllocation.map((t) => (
                      <option key={t.team} value={t.team}>
                        {t.team}
                      </option>
                    ))}
                  </select>
                )}
                {spendScope === 'developer' && (
                  <select
                    value={selectedDeveloper}
                    onChange={(e) => setSelectedDeveloper(e.target.value)}
                    className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700"
                  >
                    {developerAllocation.map((d) => (
                      <option key={d.developer} value={d.developer}>
                        {d.developer}
                      </option>
                    ))}
                  </select>
                )}
              </div>
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <Kpi
                label={spendScope === 'org' ? 'Total AI spend' : `Spend — ${spendScope === 'team' ? selectedTeam : selectedDeveloper}`}
                value={currency(scopedKpis.totalSpend)}
              />
              <Kpi label="Average cost / PR" value={`$${scopedKpis.costPerPr.toFixed(2)}`} />
              <Kpi label="Cost / dev / day" value={`$${scopedKpis.costPerDevDay.toFixed(2)}`} />
            </div>
          </div>

          {/* Cost per merged PR */}
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-1">
              <h3 className="text-sm font-semibold text-slate-900">Cost per merged PR</h3>
              <p className="mb-4 text-xs text-slate-500">
                Measure what each merged change actually cost to ship — the unit-economics view procurement and
                Finance ask for at every renewal cycle, calculated continuously from real engineering data.
              </p>
              <p className="text-3xl font-semibold text-slate-900">${kpis.costPerPr.toFixed(2)}</p>
              <p className="text-xs text-slate-400">org-wide average, {windowLabel.toLowerCase()}</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-2">
              <p className="mb-2 text-sm text-slate-500">Cost per merged PR — trend</p>
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
              Track AI spend trends across every tool. Plots daily cost across Claude, Copilot, and Cursor over the
              selected range — see whether a tool's cost is climbing faster than usage, and catch the inflection
              point before it becomes a budget conversation.
            </p>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={dailySpend}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} interval={3} />
                  <YAxis tickFormatter={(v) => `$${v}`} tick={{ fontSize: 12 }} />
                  <Tooltip formatter={(v: number) => `$${v}`} />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Line type="monotone" dataKey="claudeCode" name="Claude Code" stroke={TOOL_COLORS['Claude Code']} strokeWidth={2} dot={false} isAnimationActive={false} />
                  <Line type="monotone" dataKey="githubCopilot" name="GitHub Copilot" stroke={TOOL_COLORS['GitHub Copilot']} strokeWidth={2} dot={false} isAnimationActive={false} />
                  <Line type="monotone" dataKey="cursor" name="Cursor" stroke={TOOL_COLORS.Cursor} strokeWidth={2} dot={false} isAnimationActive={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
            <div className="mt-3 flex flex-wrap gap-4 border-t border-slate-100 pt-3 text-xs text-slate-500">
              {spendByTool.map((s) => (
                <span key={s.tool} className="flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full" style={{ backgroundColor: TOOL_COLORS[s.tool] }} />
                  {s.tool}: <span className="font-medium text-slate-800">{currency(s.monthlySpend)}</span>
                  <span className="text-slate-400">({((s.monthlySpend / totalSpend) * 100).toFixed(0)}%)</span>
                </span>
              ))}
            </div>
          </div>

          {/* Team-level allocation */}
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <h3 className="text-sm font-semibold text-slate-900">Team-level allocation</h3>
            <p className="mb-3 text-xs text-slate-500">
              Tie AI spend to teams, initiatives, and capitalizable work. Drill from total spend into team-level
              allocation — spend, average cost per PR, and developer count for every team, the foundation for cost
              capitalization, initiative-level allocation, and the quarterly audit conversation.
            </p>
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400">
                  <th className="py-2 font-medium">Team</th>
                  <th className="py-2 font-medium">Spend</th>
                  <th className="py-2 font-medium">Merged PRs</th>
                  <th className="py-2 font-medium">Avg cost / PR</th>
                  <th className="py-2 font-medium">Developers</th>
                  <th className="py-2 font-medium">Share of spend</th>
                </tr>
              </thead>
              <tbody>
                {teamAllocation
                  .slice()
                  .sort((a, b) => b.spend - a.spend)
                  .map((t) => (
                    <tr key={t.team} className="border-b border-slate-100 last:border-0">
                      <td className="py-2 font-medium text-slate-900">{t.team}</td>
                      <td className="py-2 text-slate-700">{currency(t.spend)}</td>
                      <td className="py-2 text-slate-700">{t.mergedPrs}</td>
                      <td className="py-2 text-slate-700">${(t.spend / t.mergedPrs).toFixed(2)}</td>
                      <td className="py-2 text-slate-700">{t.developerCount}</td>
                      <td className="py-2 text-slate-400">{((t.spend / kpis.totalSpend) * 100).toFixed(0)}%</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'Adoption' && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-3 text-sm text-slate-500">Adoption rate by team (active seats / licensed seats)</p>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={adoptionByTeam} layout="vertical" margin={{ left: 24 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" tickFormatter={(v) => `${v}%`} tick={{ fontSize: 12 }} domain={[0, 100]} />
                <YAxis type="category" dataKey="team" tick={{ fontSize: 12 }} width={80} />
                <Tooltip
                  formatter={(v: number, name, item) =>
                    name === 'Adoption %'
                      ? [`${v}% (${item.payload.activeSeats}/${item.payload.licensedSeats} seats)`, name]
                      : [v, name]
                  }
                />
                <Bar dataKey="adoptionPct" name="Adoption %" fill="#0f172a" radius={[0, 4, 4, 0]} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <p className="mt-2 text-xs text-slate-400">
            Adoption per BRD E9-S1: share of active engineers with ≥1 AI-attributed commit in the window, per team.
          </p>
        </div>
      )}

      {tab === 'Impact' && (
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-3 text-sm text-slate-500">AI-assisted vs. non-AI delta</p>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={impact}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="metric" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="aiAssisted" name="AI-assisted" fill="#dc2f7e" isAnimationActive={false} />
                <Bar dataKey="nonAi" name="Non-AI" fill="#94a3b8" isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <p className="mt-2 text-xs text-slate-400">
            Per BRD E9-S2: comparisons control for obvious confounders where feasible; lower is better for all metrics
            shown.
          </p>
        </div>
      )}

      {tab === 'ROI' && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <div className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
            <p className="text-sm text-slate-500">Estimated monthly value recovered</p>
            <p className="mt-1 text-3xl font-semibold text-emerald-600">{currency(roi.dollarValueRecovered)}</p>
            <p className="mt-1 text-xs text-slate-400">vs. {currency(roi.monthlySpend)} spent this month</p>
            <div className="mt-4 flex items-center gap-2">
              <span className="rounded-full bg-emerald-50 px-2.5 py-1 text-sm font-semibold text-emerald-700">
                {roi.roiMultiple.toFixed(1)}x ROI
              </span>
            </div>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-2">
            <p className="mb-3 text-sm text-slate-500">Assumptions (adjustable in Admin → AI ROI settings)</p>
            <dl className="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-slate-400">Hours saved / month</dt>
                <dd className="font-medium text-slate-900">{roi.estimatedHoursSavedPerMonth.toLocaleString()}</dd>
              </div>
              <div>
                <dt className="text-slate-400">Blended hourly rate</dt>
                <dd className="font-medium text-slate-900">${roi.blendedHourlyRate}/hr</dd>
              </div>
              <div>
                <dt className="text-slate-400">Monthly AI spend</dt>
                <dd className="font-medium text-slate-900">{currency(roi.monthlySpend)}</dd>
              </div>
            </dl>
            <p className="mt-4 text-xs text-slate-400">{roi.methodologyNote}</p>
          </div>
        </div>
      )}
    </section>
  )
}
