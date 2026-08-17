import { useState } from 'react'
import { login } from '../api'

export default function Login({ onSuccess, onBack }: { onSuccess: () => void; onBack: () => void }) {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email)
      onSuccess()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-50 text-slate-900">
      {/* KPMG masthead — matches the in-app shell so branding is consistent before login. */}
      <header className="flex h-14 shrink-0 items-center gap-3 bg-[#00338D] px-5 text-white">
        <span className="flex h-8 shrink-0 items-center justify-center rounded bg-white px-2.5 text-xs font-extrabold tracking-tight text-[#00338D]">
          KPMG
        </span>
        <div className="hidden h-6 w-px bg-white/25 sm:block" />
        <div className="hidden sm:block">
          <div className="text-sm font-semibold leading-tight text-white">AI Impact Evaluation</div>
          <div className="text-[10px] text-blue-100">Engineering Intelligence &amp; Analytics Platform</div>
        </div>
      </header>

      <div className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm rounded-3xl border border-slate-100 bg-white p-8 shadow-xl shadow-slate-200/60">
          <button onClick={onBack} className="mb-6 text-sm font-medium text-slate-400 hover:text-slate-900">
            ← Back
          </button>
          <h1 className="text-2xl font-bold tracking-tight">Log in to AI Impact Evaluation</h1>
          <p className="mt-1 text-sm text-slate-500">
            Pilot auth bridge (ADR-0004). Your role and access come from your account — an admin sets those up
            in the Admin console, not you at sign-in. Production uses SSO/OIDC.
          </p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-5">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-400">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-kpmg-400 focus:ring-2 focus:ring-kpmg-100"
              />
            </div>

            {error && (
              <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-[#00338D] px-4 py-2.5 text-sm font-semibold text-white shadow-md shadow-kpmg-900/15 hover:bg-kpmg-700 disabled:opacity-50"
            >
              {loading ? 'Signing in…' : 'Continue'}
            </button>

            <p className="text-center text-xs text-slate-400">
              First time setting this up? Signing in with the very first email on a fresh install makes that
              account an Admin automatically.
            </p>
          </form>
        </div>
      </div>
    </div>
  )
}