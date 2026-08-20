import { useEffect, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { fetchTeams, type Team } from '../api'
import Cockpit from './Cockpit'

const ACCENTS = ['#00338D', '#0091DA', '#0d9488', '#f59e0b', '#dc2626', '#478aff']

function TeamsOverviewChart({ teams }: { teams: Team[] }) {
  const data = teams.map((t, i) => ({ name: t.name, repos: t.repoCount, fill: ACCENTS[i % ACCENTS.length] }))
  return (
    <div className="mb-6 rounded-2xl border border-slate-100 bg-white p-5 shadow-sm shadow-slate-100">
      <p className="mb-3 text-sm font-medium text-slate-500">Repositories by team</p>
      <div className="h-48">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
            <XAxis dataKey="name" tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} />
            <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: '#64748b' }} axisLine={false} tickLine={false} width={30} />
            <Tooltip formatter={(v: number) => [v, 'Repositories']} cursor={{ fill: '#f8fafc' }} />
            <Bar dataKey="repos" radius={[6, 6, 0, 0]} isAnimationActive={false}>
              {data.map((d, i) => (
                <Cell key={i} fill={d.fill} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

/** Org → team drill-down (E4-S2): pick a team, then see the same Cockpit tiles scoped to it. */
export default function Teams() {
  const [teams, setTeams] = useState<Team[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<Team | null>(null)

  function load() {
    setError(null)
    fetchTeams()
      .then(setTeams)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  if (selected) {
    return (
      <div>
        <button
          onClick={() => setSelected(null)}
          className="mb-4 text-sm font-medium text-slate-500 hover:text-slate-900"
        >
          ← All teams
        </button>
        <Cockpit scope={selected.id} title={selected.name} />
      </div>
    )
  }

  return (
    <section>
      <div className="mb-1 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-kpmg-600 to-cobalt-600 text-sm text-white">
            ◆
          </span>
          <h2 className="text-xl font-bold tracking-tight text-slate-900">Teams</h2>
        </div>
        <button
          onClick={load}
          className="rounded-md border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-500 hover:border-slate-300 hover:text-slate-900"
        >
          Refresh
        </button>
      </div>
      <p className="mb-6 text-sm text-slate-500">
        {loading && 'Loading teams…'}
        {error && `Could not load teams: ${error}`}
        {!loading && !error && teams?.length === 0 && 'No teams imported yet.'}
        {!loading && !error && teams && teams.length > 0 && 'Select a team to drill into its metrics.'}
      </p>
      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          The teams API is unreachable. Check that api-core is running, then reload.
        </div>
      )}
      {!error && teams && teams.length > 0 && <TeamsOverviewChart teams={teams} />}
      {!error && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
          {(teams ?? []).map((team, i) => (
            <button
              key={team.id}
              onClick={() => setSelected(team)}
              className="group relative overflow-hidden rounded-2xl border border-slate-100 bg-white p-5 text-left shadow-sm shadow-slate-100 transition hover:-translate-y-0.5 hover:shadow-md hover:shadow-slate-200"
            >
              <span
                className="absolute inset-y-0 left-0 w-1.5"
                style={{ backgroundColor: ACCENTS[i % ACCENTS.length] }}
              />
              <div className="pl-2">
                <p className="font-semibold text-slate-900">{team.name}</p>
                <p className="mt-1 text-xs text-slate-400">
                  {team.repoCount} {team.repoCount === 1 ? 'repository' : 'repositories'}
                </p>
                <span className="mt-3 inline-flex items-center text-xs font-medium text-kpmg-600 opacity-0 transition group-hover:opacity-100">
                  View Cockpit →
                </span>
              </div>
            </button>
          ))}
          {loading &&
            [1, 2].map((i) => (
              <div key={i} className="h-24 animate-pulse rounded-2xl border border-slate-100 bg-white" />
            ))}
        </div>
      )}
      {!loading && !error && teams?.length === 0 && (
        <p className="mt-4 text-sm text-slate-400">
          Teams appear once connector-github's team backfill imports them (PRD E2-S2).
        </p>
      )}
    </section>
  )
}
