import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { investmentProfile } from '../mock/mockData'

const SLICE_COLORS = ['#0f172a', '#334155', '#64748b', '#dc2626']

export default function InvestmentProfile() {
  const { windowLabel, breakdown, trend, byTeam } = investmentProfile
  const totalHours = breakdown.reduce((sum, s) => sum + s.hours, 0)
  const unplannedShare = breakdown.find((s) => s.category === 'Unplanned / firefighting')!.hours / totalHours

  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <h2 className="text-xl font-semibold">Investment Profile</h2>
        <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">Demo data</span>
      </div>
      <p className="mb-6 text-sm text-slate-500">{windowLabel} · planned vs. unplanned engineering time</p>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-1">
          <p className="text-sm text-slate-500">Time by category</p>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={breakdown}
                  dataKey="hours"
                  nameKey="category"
                  innerRadius={50}
                  outerRadius={85}
                  paddingAngle={2}
                  isAnimationActive={false}
                >
                  {breakdown.map((_, i) => (
                    <Cell key={i} fill={SLICE_COLORS[i % SLICE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(v: number) => `${v} hrs`} />
                <Legend verticalAlign="bottom" height={48} wrapperStyle={{ fontSize: 12 }} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <p className="mt-2 text-xs text-slate-400">{(unplannedShare * 100).toFixed(0)}% of time went to unplanned work this window.</p>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-4 lg:col-span-2">
          <p className="text-sm text-slate-500">Planned vs. unplanned trend</p>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={trend} stackOffset="expand">
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                <YAxis tickFormatter={(v) => `${(v * 100).toFixed(0)}%`} tick={{ fontSize: 12 }} />
                <Tooltip formatter={(v: number) => `${v}%`} />
                <Legend wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="planned" stackId="a" name="Planned" fill="#0f172a" isAnimationActive={false} />
                <Bar dataKey="unplanned" stackId="a" name="Unplanned" fill="#dc2626" isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
        <p className="mb-3 text-sm text-slate-500">Planned share by team</p>
        <div className="h-56">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={byTeam} layout="vertical" margin={{ left: 24 }}>
              <CartesianGrid strokeDasharray="3 3" horizontal={false} />
              <XAxis type="number" tickFormatter={(v) => `${v}%`} tick={{ fontSize: 12 }} />
              <YAxis type="category" dataKey="team" tick={{ fontSize: 12 }} width={80} />
              <Tooltip formatter={(v: number) => `${v}%`} />
              <Bar dataKey="planned" name="Planned %" fill="#0f172a" radius={[0, 4, 4, 0]} isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </section>
  )
}
