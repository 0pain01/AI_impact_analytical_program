import { useEffect, useState } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { fetchCockpit, type CockpitResponse, type CockpitTile, type MetricKey } from '../api'

type Tier = 'Elite' | 'High' | 'Medium' | 'Low'

const TIER_STYLE: Record<Tier, { text: string; bg: string; bar: string }> = {
  Elite: { text: 'text-emerald-700', bg: 'bg-emerald-50', bar: '#059669' },
  High: { text: 'text-blue-700', bg: 'bg-blue-50', bar: '#2563eb' },
  Medium: { text: 'text-amber-700', bg: 'bg-amber-50', bar: '#d97706' },
  Low: { text: 'text-red-700', bg: 'bg-red-50', bar: '#dc2626' },
}

const NEUTRAL_BAR = '#334155'

// DORA 2023 State of DevOps performance bands (Google Cloud). Applied only to the four core
// DORA metrics — PR velocity/cycle time are supplementary and shown without a tier.
function deploymentFrequencyTier(sampleSize: number, windowDays: number): Tier {
  const perDay = sampleSize / windowDays
  if (perDay >= 1) return 'Elite'
  if (perDay >= 1 / 7) return 'High'
  if (perDay >= 1 / 30) return 'Medium'
  return 'Low'
}

function leadTimeTier(hours: number): Tier {
  if (hours < 24) return 'Elite'
  if (hours < 168) return 'High'
  if (hours < 720) return 'Medium'
  return 'Low'
}

function changeFailureRateTier(rate: number): Tier {
  if (rate <= 0.15) return 'Elite'
  if (rate <= 0.3) return 'High'
  if (rate <= 0.45) return 'Medium'
  return 'Low'
}

function mttrTier(hours: number): Tier {
  if (hours < 1) return 'Elite'
  if (hours < 24) return 'High'
  if (hours < 168) return 'Medium'
  return 'Low'
}

const TIER_ORDER: Tier[] = ['Low', 'Medium', 'High', 'Elite']
const TIER_SCORE: Record<Tier, number> = { Elite: 100, High: 75, Medium: 50, Low: 25 }
const RADAR_LABEL: Partial<Record<MetricKey, string>> = {
  deployment_frequency: 'Deploy freq.',
  lead_time_p50_hours: 'Lead time',
  change_failure_rate: 'Change fail rate',
  mttr_p50_hours: 'MTTR',
}

function tierFor(tile: CockpitTile, windowDays: number): Tier | null {
  if (tile.aggregate === null) return null
  const n = Number(tile.aggregate)
  switch (tile.metricKey) {
    case 'deployment_frequency':
      return deploymentFrequencyTier(tile.sampleSize, windowDays)
    case 'lead_time_p50_hours':
      return leadTimeTier(n)
    case 'change_failure_rate':
      return changeFailureRateTier(n)
    case 'mttr_p50_hours':
      return mttrTier(n)
    default:
      return null
  }
}

function formatValue(tile: CockpitTile): string {
  if (tile.aggregate === null) return '—'
  const n = Number(tile.aggregate)
  if (tile.unit === '%') return `${(n * 100).toFixed(1)}%`
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function trendDelta(tile: CockpitTile, goodDirection: 'up' | 'down'): { pct: number; positive: boolean } | null {
  if (tile.series.length < 2) return null
  const first = tile.series[0].value
  const last = tile.series[tile.series.length - 1].value
  if (first === 0) return null
  const pct = ((last - first) / Math.abs(first)) * 100
  const positive = goodDirection === 'up' ? pct >= 0 : pct <= 0
  return { pct, positive }
}

const GOOD_DIRECTION: Record<MetricKey, 'up' | 'down'> = {
  deployment_frequency: 'up',
  lead_time_p50_hours: 'down',
  change_failure_rate: 'down',
  mttr_p50_hours: 'down',
  pr_velocity: 'up',
  pr_cycle_time_p50_hours: 'down',
}

function ScoreCard({ tile, windowDays }: { tile: CockpitTile; windowDays: number }) {
  const tier = tierFor(tile, windowDays)
  const delta = trendDelta(tile, GOOD_DIRECTION[tile.metricKey])
  const hasSeries = tile.series.length > 1
  const gradientId = `spark-${tile.metricKey}`
  const barColor = tier ? TIER_STYLE[tier].bar : NEUTRAL_BAR

  return (
    <div className="relative overflow-hidden rounded-lg border border-slate-200 bg-white p-4" title={tile.definition}>
      <span className="absolute inset-y-0 left-0 w-1" style={{ backgroundColor: barColor }} />
      <div className="pl-2">
        <div className="flex items-center justify-between">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{tile.label}</p>
          {tier && (
            <span className={`rounded px-1.5 py-0.5 text-[11px] font-semibold ${TIER_STYLE[tier].bg} ${TIER_STYLE[tier].text}`}>
              {tier}
            </span>
          )}
        </div>
        <div className="mt-2 flex items-baseline gap-2">
          <p className="text-2xl font-semibold tabular-nums text-slate-900">{formatValue(tile)}</p>
          <p className="text-xs text-slate-400">{tile.unit}</p>
        </div>
        <div className="mt-1 flex items-center gap-2 text-xs text-slate-400">
          <span>{tile.aggregate !== null ? `${tile.sampleSize} data point${tile.sampleSize === 1 ? '' : 's'}` : 'no data'}</span>
          {delta && (
            <span className={delta.positive ? 'font-medium text-emerald-600' : 'font-medium text-red-600'}>
              {delta.pct >= 0 ? '▲' : '▼'} {Math.abs(delta.pct).toFixed(0)}%
            </span>
          )}
        </div>
        {hasSeries && (
          <div className="mt-2 h-8">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={tile.series}>
                <defs>
                  <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={barColor} stopOpacity={0.25} />
                    <stop offset="100%" stopColor={barColor} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <Area
                  type="monotone"
                  dataKey="value"
                  stroke={barColor}
                  strokeWidth={1.5}
                  fill={`url(#${gradientId})`}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </div>
  )
}

/** Overall DORA shape at a glance — each axis scored from its Elite/High/Medium/Low tier. */
function DoraRadar({ coreTiles, windowDays }: { coreTiles: CockpitTile[]; windowDays: number }) {
  const data = coreTiles.map((t) => {
    const tier = tierFor(t, windowDays)
    return { metric: RADAR_LABEL[t.metricKey] ?? t.label, score: tier ? TIER_SCORE[tier] : 0, tier }
  })
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Overall DORA posture</p>
      <div className="h-64">
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart data={data} outerRadius="70%">
            <PolarGrid stroke="#e2e8f0" />
            <PolarAngleAxis dataKey="metric" tick={{ fontSize: 11, fill: '#64748b' }} />
            <PolarRadiusAxis domain={[0, 100]} tick={false} axisLine={false} tickCount={5} />
            <Radar dataKey="score" stroke="#334155" fill="#334155" fillOpacity={0.25} strokeWidth={2} isAnimationActive={false} />
            <Tooltip formatter={(_, _n, props) => (props.payload as { tier: Tier | null }).tier ?? 'No data'} />
          </RadarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

/** Where each core metric currently lands across the Low → Elite band. */
function TierBands({ coreTiles, windowDays }: { coreTiles: CockpitTile[]; windowDays: number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-2">
      <p className="mb-4 text-xs font-semibold uppercase tracking-wide text-slate-400">Where each metric lands</p>
      <div className="space-y-4">
        {coreTiles.map((tile) => {
          const tier = tierFor(tile, windowDays)
          return (
            <div key={tile.metricKey}>
              <div className="mb-1 flex items-center justify-between text-xs">
                <span className="font-medium text-slate-600">{tile.label}</span>
                <span className="text-slate-400">
                  {formatValue(tile)} {tile.unit}
                </span>
              </div>
              <div className="flex gap-1">
                {TIER_ORDER.map((t) => (
                  <div key={t} className="h-2 flex-1 rounded-full" style={{ backgroundColor: tier === t ? TIER_STYLE[t].bar : '#e2e8f0' }} />
                ))}
              </div>
            </div>
          )
        })}
      </div>
      <div className="mt-2 flex justify-between text-[10px] font-medium uppercase tracking-wide text-slate-400">
        <span>Low</span>
        <span>Medium</span>
        <span>High</span>
        <span>Elite</span>
      </div>
    </div>
  )
}

function HeroTrend({ tile }: { tile: CockpitTile }) {
  const accent = NEUTRAL_BAR
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5">
      <div className="mb-4 flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{tile.label} — trend</p>
        <p className="max-w-xs truncate text-xs text-slate-400" title={tile.definition}>
          {tile.definition}
        </p>
      </div>
      <div className="h-52">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={tile.series} margin={{ left: -20 }}>
            <defs>
              <linearGradient id="hero-gradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={accent} stopOpacity={0.18} />
                <stop offset="100%" stopColor={accent} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
            <XAxis
              dataKey="day"
              tickFormatter={(d) => new Date(d).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
              tick={{ fontSize: 11, fill: '#94a3b8' }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} width={40} />
            <Tooltip labelFormatter={(d) => new Date(d as string).toLocaleDateString()} formatter={(v: number) => [v, tile.label]} />
            <Area type="monotone" dataKey="value" stroke={accent} strokeWidth={2} fill="url(#hero-gradient)" isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

const OUTCOME_COLORS = ['#334155', '#dc2626']

/** Deploy success vs. failure mix, derived from deployment_frequency's sample size and
 * change_failure_rate's aggregate rate — both already computed by metrics-engine. */
function DeploymentOutcomes({ tiles }: { tiles: CockpitTile[] }) {
  const deployTile = tiles.find((t) => t.metricKey === 'deployment_frequency')
  const cfrTile = tiles.find((t) => t.metricKey === 'change_failure_rate')
  const totalDeploys = deployTile?.sampleSize ?? 0
  const failureRate = cfrTile?.aggregate !== null && cfrTile?.aggregate !== undefined ? Number(cfrTile.aggregate) : 0

  if (totalDeploys === 0) {
    return (
      <div className="flex h-full flex-col rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Deployment outcomes</p>
        <p className="mt-4 flex-1 text-sm text-slate-400">No deploys recorded in this window yet.</p>
      </div>
    )
  }

  const failed = Math.round(totalDeploys * failureRate)
  const successful = totalDeploys - failed
  // Recharts fails to draw a single 100%-share slice (zero-length arc path), so the
  // non-dominant slice gets a hairline value purely for rendering; the tooltip and legend
  // still read the real count via `count`.
  const breakdown = [
    { name: 'Successful', count: successful, plot: successful || 0.0001 },
    { name: 'Failed / rolled back', count: failed, plot: failed || 0.0001 },
  ]

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 lg:col-span-1">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Deployment outcomes</p>
      <div className="h-52">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={breakdown}
              dataKey="plot"
              nameKey="name"
              innerRadius={50}
              outerRadius={80}
              paddingAngle={2}
              isAnimationActive={false}
            >
              {breakdown.map((_, i) => (
                <Cell key={i} fill={OUTCOME_COLORS[i % OUTCOME_COLORS.length]} />
              ))}
            </Pie>
            <Tooltip formatter={(_, _n, props) => `${(props.payload as { count: number }).count} deploys`} />
            <Legend verticalAlign="bottom" height={36} wrapperStyle={{ fontSize: 12 }} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <p className="mt-2 text-xs text-slate-400">
        {(failureRate * 100).toFixed(0)}% of {totalDeploys} deploy{totalDeploys === 1 ? '' : 's'} needed a hotfix or rollback within 48h.
      </p>
    </div>
  )
}

const CORE_DORA: MetricKey[] = ['deployment_frequency', 'lead_time_p50_hours', 'change_failure_rate', 'mttr_p50_hours']

function csvCell(value: string | number): string {
  const s = String(value)
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

/** Client-side CSV of everything the tiles currently show — summary + full daily series per
 * metric — so "Export report" produces exactly what's on screen, no separate backend export
 * endpoint needed for data that's already fully in the response. */
function buildCockpitCsv(data: CockpitResponse, scope: string, windowDays: number): string {
  const rows: string[][] = []
  rows.push(['scope', scope])
  rows.push(['window_days', String(windowDays)])
  rows.push(['as_of', data.asOf ?? ''])
  rows.push([])
  rows.push(['metric', 'label', 'aggregate', 'unit', 'sample_size', 'tier'])
  for (const tile of data.tiles) {
    const tier = tierFor(tile, windowDays) ?? ''
    rows.push([
      tile.metricKey,
      tile.label,
      tile.aggregate === null ? '' : String(tile.aggregate),
      tile.unit,
      String(tile.sampleSize),
      tier,
    ])
  }
  rows.push([])
  rows.push(['metric', 'day', 'value'])
  for (const tile of data.tiles) {
    for (const point of tile.series) {
      rows.push([tile.metricKey, point.day, String(point.value)])
    }
  }
  return rows.map((r) => r.map(csvCell).join(',')).join('\n')
}

function downloadCsv(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** Reusable Cockpit view (E4-S1/E4-S2): scope is a repo, "*" for org, or a team id. */
export default function Cockpit({ scope = '*', title = 'Cockpit' }: { scope?: string; title?: string }) {
  const [data, setData] = useState<CockpitResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [days, setDays] = useState<30 | 90>(30)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchCockpit(days, scope)
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
  }, [days, scope])

  // Only the very first load (no data on screen yet) shows the skeleton — switching the
  // 30/90-day toggle re-fetches with `data` still populated from the previous window, so the
  // existing tiles stay mounted (just dimmed) instead of the whole page unmounting/remounting
  // into skeleton blocks and back, which read as a flicker.
  const isInitialLoad = loading && !data
  const isRefetching = loading && !!data
  const hasData = data?.tiles.some((t) => t.aggregate !== null) ?? false
  const coreTiles = data?.tiles.filter((t) => CORE_DORA.includes(t.metricKey)) ?? []
  const supplementaryTiles = data?.tiles.filter((t) => !CORE_DORA.includes(t.metricKey)) ?? []
  const heroTile = data?.tiles.find((t) => t.metricKey === 'deployment_frequency' && t.series.length > 1)

  return (
    <section>
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 pb-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
            {scope === '*' ? 'Organization' : 'Team'}
          </p>
          <h2 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h2>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex overflow-hidden rounded-md border border-slate-200 text-xs">
            {([30, 90] as const).map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`px-3 py-1.5 font-medium ${days === d ? 'bg-slate-900 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}
              >
                {d} days
              </button>
            ))}
          </div>
          <button
            disabled={!data || !hasData}
            onClick={() => {
              if (!data) return
              const stamp = new Date().toISOString().slice(0, 10)
              downloadCsv(`cockpit-${scope === '*' ? 'org' : scope}-${data.windowDays}d-${stamp}.csv`, buildCockpitCsv(data, scope, data.windowDays))
            }}
            className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-600 enabled:hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
            title={hasData ? 'Download the tiles above as CSV' : 'No data to export yet'}
          >
            Export report
          </button>
        </div>
      </div>

      <p className="mb-4 text-sm text-slate-500">
        {isInitialLoad && 'Loading metrics…'}
        {error && `Could not load metrics: ${error}`}
        {!error && !isInitialLoad && data?.asOf &&
          `Data as of ${new Date(data.asOf).toLocaleString()}${isRefetching ? ' · Refreshing…' : ''}`}
        {!error && !isInitialLoad && !data?.asOf && 'No data yet — connect a tool and metrics appear after the next recompute.'}
      </p>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          The metrics API is unreachable. Check that api-core is running, then reload.
        </div>
      )}

      {!error && isInitialLoad && (
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-64 animate-pulse rounded-lg border border-slate-200 bg-white" />
          ))}
        </div>
      )}

      {!error && data && (
        <div className={`transition-opacity duration-200 ${isRefetching ? 'opacity-50' : 'opacity-100'}`}>
          {coreTiles.length > 0 && (
            <>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">DORA performance</p>
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
                <DoraRadar coreTiles={coreTiles} windowDays={data?.windowDays ?? 30} />
                <TierBands coreTiles={coreTiles} windowDays={data?.windowDays ?? 30} />
              </div>

              <p className="mb-2 mt-5 text-xs font-semibold uppercase tracking-wide text-slate-400">Metric detail</p>
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                {coreTiles.map((tile) => (
                  <ScoreCard key={tile.metricKey} tile={tile} windowDays={data?.windowDays ?? 30} />
                ))}
              </div>

              {supplementaryTiles.length > 0 && (
                <>
                  <p className="mb-2 mt-5 text-xs font-semibold uppercase tracking-wide text-slate-400">Review throughput</p>
                  <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                    {supplementaryTiles.map((tile) => (
                      <ScoreCard key={tile.metricKey} tile={tile} windowDays={data?.windowDays ?? 30} />
                    ))}
                  </div>
                </>
              )}
            </>
          )}

          {hasData && (
            <div className="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-3">
              <DeploymentOutcomes tiles={data.tiles} />
              <div className="lg:col-span-2">
                {heroTile ? (
                  <HeroTrend tile={heroTile} />
                ) : (
                  <div className="flex h-full min-h-[16rem] items-center justify-center rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
                    Trend chart appears once the window has 2+ days of data.
                  </div>
                )}
              </div>
            </div>
          )}

          {!hasData && (
            <p className="mt-4 text-sm text-slate-400">Tiles stay empty until staged events exist for the selected window.</p>
          )}
        </div>
      )}
    </section>
  )
}
