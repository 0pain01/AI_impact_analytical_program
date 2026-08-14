import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { codeReview } from '../mock/mockData'

function ageBadge(hours: number) {
  if (hours >= 72) return 'bg-red-100 text-red-700'
  if (hours >= 48) return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

export default function CodeReview() {
  const { windowLabel, cycleStages, reviewLoad, agingPrs } = codeReview

  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <h2 className="text-xl font-semibold">Code Review Analytics</h2>
        <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">Demo data</span>
      </div>
      <p className="mb-6 text-sm text-slate-500">{windowLabel}</p>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-3 text-sm text-slate-500">PR cycle time breakdown (p50 hours)</p>
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
        </div>

        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <p className="mb-3 text-sm text-slate-500">Review load by reviewer</p>
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
        </div>
      </div>

      <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
        <p className="mb-3 text-sm text-slate-500">Aging pull requests (flag before they block release)</p>
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400">
              <th className="pb-2 pr-4">PR</th>
              <th className="pb-2 pr-4">Repo</th>
              <th className="pb-2 pr-4">Author</th>
              <th className="pb-2 pr-4">Reviewers</th>
              <th className="pb-2 pr-4">Size</th>
              <th className="pb-2">Age</th>
            </tr>
          </thead>
          <tbody>
            {agingPrs.map((pr) => (
              <tr key={pr.id} className="border-b border-slate-100 last:border-0">
                <td className="py-2 pr-4">
                  <p className="font-medium">{pr.id}</p>
                  <p className="text-xs text-slate-400">{pr.title}</p>
                </td>
                <td className="py-2 pr-4 text-slate-600">{pr.repo}</td>
                <td className="py-2 pr-4 text-slate-600">{pr.author}</td>
                <td className="py-2 pr-4 text-slate-600">{pr.reviewers.join(', ')}</td>
                <td className="py-2 pr-4 text-slate-600">{pr.sizeLines} lines</td>
                <td className="py-2">
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${ageBadge(pr.ageHours)}`}>{pr.ageHours}h</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
