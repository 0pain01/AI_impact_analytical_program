import { useEffect, useState } from 'react'
import {
  addTeamRepo,
  connectGithubOrgTeams,
  connectRepo,
  createAdminUser,
  createOrUpdateTeam,
  deleteTeam,
  disconnectRepo,
  fetchAdminConnectors,
  fetchAdminUsers,
  fetchAuditLog,
  fetchRepoSyncStatus,
  fetchTeams,
  removeTeamRepo,
  setAdminUserActive,
  updateAdminUserGithubLogin,
  updateAdminUserRole,
  type AdminUser,
  type AuditEntry,
  type ConnectorHealth,
  type RepoSyncStatus,
  type Role,
  type Team,
} from '../api'

const ROLES: Role[] = ['ADMIN', 'ENG_LEADER', 'MANAGER', 'IC', 'FINANCE_READONLY']

function connectorStatusBadge(status: ConnectorHealth['status']) {
  if (status === 'CONNECTED') return 'bg-emerald-100 text-emerald-700'
  if (status === 'STALE') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-500'
}

function connectorStatusLabel(status: ConnectorHealth['status']) {
  if (status === 'CONNECTED') return 'Connected'
  if (status === 'STALE') return 'Stale'
  return 'Not connected'
}

function formatTimestamp(iso: string | null) {
  return iso ? new Date(iso).toLocaleString() : 'Never'
}

/** Hover-triggered help bubble — a small "i" badge next to a panel/section heading. */
function InfoTooltip({ text }: { text: string }) {
  return (
    <span className="group relative inline-flex items-center">
      <span
        tabIndex={0}
        className="flex h-4 w-4 shrink-0 cursor-help items-center justify-center rounded-full bg-slate-200 text-[10px] font-bold leading-none text-slate-500 hover:bg-slate-300"
      >
        i
      </span>
      <span className="pointer-events-none absolute left-1/2 top-full z-20 mt-1.5 w-64 -translate-x-1/2 rounded-md bg-slate-900 px-2.5 py-1.5 text-xs font-normal normal-case leading-snug text-white opacity-0 shadow-lg transition-opacity duration-100 group-hover:opacity-100 group-focus-within:opacity-100">
        {text}
      </span>
    </span>
  )
}

function ConnectorsPanel() {
  const [connectors, setConnectors] = useState<ConnectorHealth[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchAdminConnectors()
      .then((c) => {
        if (!cancelled) setConnectors(c)
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-3 flex items-center gap-2">
        <p className="text-sm text-slate-500">Connectors</p>
        <InfoTooltip text="Health of each tool integration (GitHub, GitHub Actions, Jira, Jenkins). Status is based on Last checked, not Last data change — a connector that runs fine but simply has nothing new to report (e.g. no Jira issues touched since the last check) still shows Connected, not Stale." />
        <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">Live</span>
      </div>
      {error && (
        <p className="text-sm text-red-600">Could not load connector health: {error}</p>
      )}
      {!error && !connectors && <p className="text-sm text-slate-400">Loading…</p>}
      {!error && connectors && (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400">
              <th className="pb-2 pr-4">Tool</th>
              <th className="pb-2 pr-4">Type</th>
              <th className="pb-2 pr-4">Status</th>
              <th className="pb-2 pr-4">Events ingested</th>
              <th className="pb-2 pr-4">
                <span className="inline-flex items-center gap-1">
                  Last checked
                  <InfoTooltip text="Last time this connector reported in at all, including a re-check that found nothing new. This is what Status is based on." />
                </span>
              </th>
              <th className="pb-2">
                <span className="inline-flex items-center gap-1">
                  Last data change
                  <InfoTooltip text="Last time something actually new landed (a genuinely new PR, issue, build, etc). Can lag behind Last checked when a connector is healthy but its source has been quiet." />
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            {connectors.map((c) => (
              <tr key={c.key} className="border-b border-slate-100 last:border-0">
                <td className="py-2 pr-4 font-medium">{c.name}</td>
                <td className="py-2 pr-4 text-slate-600">{c.type}</td>
                <td className="py-2 pr-4">
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${connectorStatusBadge(c.status)}`}>
                    {connectorStatusLabel(c.status)}
                  </span>
                </td>
                <td className="py-2 pr-4 text-slate-600">{c.eventCount.toLocaleString()}</td>
                <td className="py-2 pr-4 text-slate-600">{formatTimestamp(c.lastCheckedAt)}</td>
                <td className="py-2 text-slate-600">{formatTimestamp(c.lastDataChangeAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <p className="mt-3 text-xs text-slate-400">
        SonarQube, PagerDuty, and AI-assistant telemetry connectors are planned (PRD E1, Should-have) but not built yet.
      </p>
    </div>
  )
}

function CreateTeamForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) {
      setError('Team name is required.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await createOrUpdateTeam(name.trim(), null)
      setName('')
      onCreated()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create team.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-2">
      <div>
        <label className="mb-1 block text-xs text-slate-400">Team name</label>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Platform Team"
          className="w-44 rounded-md border border-slate-200 px-2 py-1 text-sm"
        />
      </div>
      <button
        type="submit"
        disabled={busy}
        className="rounded-md border border-slate-300 px-3 py-1 text-sm font-medium text-slate-700 disabled:opacity-50"
      >
        {busy ? 'Adding…' : 'Create team'}
      </button>
      {error && <p className="w-full text-sm text-red-600">{error}</p>}
    </form>
  )
}

function ConnectGithubTeamsForm() {
  const [org, setOrg] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!org.trim()) {
      setError('Org is required.')
      return
    }
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await connectGithubOrgTeams(org.trim())
      setMessage(`Importing ${org.trim()}'s teams in the background — new teams will appear below shortly.`)
      setOrg('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not import org teams.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-2">
      <div>
        <label className="mb-1 block text-xs text-slate-400">GitHub org</label>
        <input
          value={org}
          onChange={(e) => setOrg(e.target.value)}
          placeholder="my-org"
          className="w-40 rounded-md border border-slate-200 px-2 py-1 text-sm"
        />
      </div>
      <button
        type="submit"
        disabled={busy}
        className="rounded-md border border-slate-300 px-3 py-1 text-sm font-medium text-slate-700 disabled:opacity-50"
      >
        {busy ? 'Importing…' : 'Import org teams'}
      </button>
      {error && <p className="w-full text-sm text-red-600">{error}</p>}
      {message && <p className="w-full text-sm text-emerald-700">{message}</p>}
    </form>
  )
}

function ConnectRepoForm({ teams, onConnected }: { teams: Team[]; onConnected: () => void }) {
  const [owner, setOwner] = useState('')
  const [repo, setRepo] = useState('')
  const [teamId, setTeamId] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!owner.trim() || !repo.trim()) {
      setError('Owner and repo are both required.')
      return
    }
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await connectRepo(owner.trim(), repo.trim(), teamId || null)
      setMessage(
        `Connecting ${owner.trim()}/${repo.trim()}${teamId ? ' and assigning it to the selected team' : ''} — ` +
          'watch its progress in the Sync status table below.',
      )
      setOwner('')
      setRepo('')
      setTeamId('')
      onConnected()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not connect repo.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-2">
      <div>
        <label className="mb-1 block text-xs text-slate-400">Owner</label>
        <input
          value={owner}
          onChange={(e) => setOwner(e.target.value)}
          placeholder="octocat"
          className="w-28 rounded-md border border-slate-200 px-2 py-1 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs text-slate-400">Repo</label>
        <input
          value={repo}
          onChange={(e) => setRepo(e.target.value)}
          placeholder="hello-world"
          className="w-36 rounded-md border border-slate-200 px-2 py-1 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs text-slate-400">Team (optional)</label>
        <select
          value={teamId}
          onChange={(e) => setTeamId(e.target.value)}
          className="rounded-md border border-slate-200 px-2 py-1 text-sm"
        >
          <option value="">No team</option>
          {teams.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
      </div>
      <button
        type="submit"
        disabled={busy}
        className="rounded-md bg-slate-900 px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
      >
        {busy ? 'Connecting…' : 'Connect repo'}
      </button>
      {error && <p className="w-full text-sm text-red-600">{error}</p>}
      {message && <p className="w-full text-sm text-emerald-700">{message}</p>}
    </form>
  )
}

function syncStateBadge(state: RepoSyncStatus['syncState']) {
  if (state === 'COMPLETED') return 'bg-emerald-100 text-emerald-700'
  if (state === 'IN_PROGRESS') return 'bg-amber-100 text-amber-700'
  return 'bg-red-100 text-red-700'
}

function syncStateLabel(state: RepoSyncStatus['syncState']) {
  if (state === 'COMPLETED') return 'Synced'
  if (state === 'IN_PROGRESS') return 'Syncing…'
  return 'Failed'
}

function RepoSyncTable({
  teams,
  refreshSignal,
  onTeamsChanged,
}: {
  teams: Team[]
  refreshSignal: number
  onTeamsChanged: () => void
}) {
  const [rows, setRows] = useState<RepoSyncStatus[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyRepo, setBusyRepo] = useState<string | null>(null)

  function load() {
    fetchRepoSyncStatus()
      .then((r) => setRows(r))
      .catch((e: Error) => setError(e.message))
  }

  useEffect(load, [refreshSignal])

  // Live status: poll while anything is actively syncing, stop once everything has settled —
  // no point polling a quiet table.
  useEffect(() => {
    if (!rows || !rows.some((r) => r.syncState === 'IN_PROGRESS')) return
    const id = setInterval(load, 15000)
    return () => clearInterval(id)
  }, [rows])

  async function handleRefresh(repo: string) {
    const [owner, name] = repo.split('/')
    if (!owner || !name) return
    setBusyRepo(repo)
    setError(null)
    try {
      await connectRepo(owner, name, null)
      load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not refresh repo.')
    } finally {
      setBusyRepo(null)
    }
  }

  async function handleAssign(repo: string, teamId: string) {
    if (!teamId) return
    setBusyRepo(repo)
    setError(null)
    try {
      await addTeamRepo(teamId, repo)
      load()
      onTeamsChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not assign repo to team.')
    } finally {
      setBusyRepo(null)
    }
  }

  async function handleUnassign(repo: string, teamId: string) {
    setBusyRepo(repo)
    setError(null)
    try {
      await removeTeamRepo(teamId, repo)
      load()
      onTeamsChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not remove repo from team.')
    } finally {
      setBusyRepo(null)
    }
  }

  async function handleDelete(repo: string) {
    if (!confirm(`Remove ${repo} from Cockpit/Admin? Its raw ingested events stay in the audit log — reconnecting later re-derives the same data.`)) {
      return
    }
    setBusyRepo(repo)
    setError(null)
    try {
      await disconnectRepo(repo)
      load()
      onTeamsChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete repo.')
    } finally {
      setBusyRepo(null)
    }
  }

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
          Sync status
          <InfoTooltip text="Syncing = backfill running now. Synced = data has landed in staging — Cockpit's DORA numbers refresh on a recompute cycle that runs every 5 minutes, so allow a little longer after Synced before checking there. Failed = the last attempt errored; Refresh to retry. Delete removes the repo from Cockpit/Admin (and any team) — the raw ingested events stay in the audit log, so reconnecting later re-derives the same data." />
        </p>
        <button onClick={load} className="text-xs font-medium text-slate-500 hover:text-slate-900">
          Refresh all
        </button>
      </div>
      {error && <p className="mb-2 text-xs text-red-600">{error}</p>}
      {!rows && <p className="text-xs text-slate-400">Loading…</p>}
      {rows && rows.length === 0 && (
        <p className="text-xs text-slate-400">No repos connected yet — use the form above.</p>
      )}
      {rows && rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-200 uppercase tracking-wide text-slate-400">
                <th className="pb-2 pr-3">Repo</th>
                <th className="pb-2 pr-3">Team</th>
                <th className="pb-2 pr-3">Status</th>
                <th className="pb-2 pr-3">Last synced</th>
                <th className="pb-2 pr-3">Events</th>
                <th className="pb-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.repo} className="border-b border-slate-100 last:border-0">
                  <td className="py-2 pr-3 font-medium text-slate-700">{r.repo}</td>
                  <td className="py-2 pr-3 text-slate-600">
                    <div className="flex flex-wrap items-center gap-1">
                      {r.teams.map((t) => {
                        const team = teams.find((x) => x.name === t)
                        return (
                          <span
                            key={t}
                            className="flex items-center gap-1 rounded bg-slate-100 px-1.5 py-0.5 text-slate-600"
                          >
                            {t}
                            {team && (
                              <button
                                onClick={() => handleUnassign(r.repo, team.id)}
                                disabled={busyRepo === r.repo}
                                className="text-slate-400 hover:text-red-600 disabled:opacity-50"
                                title="Remove from this team"
                              >
                                ×
                              </button>
                            )}
                          </span>
                        )
                      })}
                      {r.teams.length === 0 && <span className="text-slate-400">— unassigned —</span>}
                      {teams.filter((t) => !r.teams.includes(t.name)).length > 0 && (
                        <select
                          defaultValue=""
                          disabled={busyRepo === r.repo}
                          onChange={(e) => {
                            const value = e.target.value
                            e.target.value = ''
                            handleAssign(r.repo, value)
                          }}
                          className="rounded border border-slate-200 px-1 py-0.5 text-[11px] text-slate-500"
                        >
                          <option value="" disabled>
                            + team
                          </option>
                          {teams
                            .filter((t) => !r.teams.includes(t.name))
                            .map((t) => (
                              <option key={t.id} value={t.id}>
                                {t.name}
                              </option>
                            ))}
                        </select>
                      )}
                    </div>
                  </td>
                  <td className="py-2 pr-3">
                    <span className={`rounded px-2 py-0.5 font-medium ${syncStateBadge(r.syncState)}`}>
                      {syncStateLabel(r.syncState)}
                    </span>
                    {r.syncError && <p className="mt-0.5 max-w-[16rem] text-[11px] text-red-600">{r.syncError}</p>}
                  </td>
                  <td className="py-2 pr-3 text-slate-600">{formatTimestamp(r.lastSyncAt)}</td>
                  <td className="py-2 pr-3 text-slate-600">{r.eventCount.toLocaleString()}</td>
                  <td className="py-2 text-right">
                    <div className="flex justify-end gap-3">
                      <button
                        onClick={() => handleRefresh(r.repo)}
                        disabled={busyRepo === r.repo || r.syncState === 'IN_PROGRESS'}
                        className="text-xs font-medium text-slate-500 hover:text-slate-900 disabled:opacity-50"
                      >
                        {r.syncState === 'IN_PROGRESS' ? 'Syncing…' : 'Refresh'}
                      </button>
                      <button
                        onClick={() => handleDelete(r.repo)}
                        disabled={busyRepo === r.repo}
                        className="text-xs font-medium text-slate-500 hover:text-red-600 disabled:opacity-50"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function RepoTeamsPanel() {
  const [teams, setTeams] = useState<Team[]>([])
  const [teamsError, setTeamsError] = useState<string | null>(null)
  const [syncRefreshSignal, setSyncRefreshSignal] = useState(0)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  function reloadTeams() {
    fetchTeams()
      .then(setTeams)
      .catch((e: Error) => setTeamsError(e.message))
  }

  useEffect(reloadTeams, [])

  async function handleDeleteTeam(team: Team) {
    if (!confirm(`Delete "${team.name}"? This unmaps its ${team.repoCount} repo(s) and any members — repo data itself is untouched.`)) {
      return
    }
    setDeleteError(null)
    setDeletingId(team.id)
    try {
      await deleteTeam(team.id)
      reloadTeams()
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : `Could not delete "${team.name}".`)
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-1 flex items-center gap-2">
        <p className="text-sm text-slate-500">Repos &amp; Teams</p>
        <InfoTooltip text="Connect a GitHub repo to start pulling its PR/commit/CI data, organize repos into teams, and watch sync progress — all in one place. Replaces calling connector-github's backfill endpoints from a terminal." />
      </div>
      <p className="mb-4 text-xs text-slate-400">
        Connecting a repo does not immediately populate Cockpit — see the Sync status table below for progress, and
        allow a few minutes after &quot;Synced&quot; for Cockpit&apos;s DORA numbers to refresh.
      </p>

      <div className="mb-4 grid grid-cols-1 gap-4 border-b border-slate-100 pb-4 sm:grid-cols-2">
        <div>
          <p className="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
            Step 1 · Create a team
            <InfoTooltip text="Optional. Set this up first if you want to assign a repo to a team the moment you connect it below — or skip this and assign teams later from the Sync status table." />
          </p>
          <CreateTeamForm onCreated={reloadTeams} />
          {teamsError && <p className="mt-1 text-xs text-red-600">{teamsError}</p>}
        </div>
        <div>
          <p className="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
            Bulk alternative
            <InfoTooltip text="Imports ALL of a GitHub org's teams, their repos, and members automatically — use this instead of connecting repos one by one and assigning teams by hand, if you manage a whole org." />
          </p>
          <ConnectGithubTeamsForm />
        </div>
      </div>

      <div className="mb-4 border-b border-slate-100 pb-4">
        <p className="mb-2 flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
          Step 2 · Connect a repo
          <InfoTooltip text="Pulls PRs, commits, and workflow-run (CI/CD) history for this repo into the pipeline. Pick a team here to assign it in the same step, or assign it later from the table below." />
        </p>
        <ConnectRepoForm
          teams={teams}
          onConnected={() => {
            setSyncRefreshSignal((k) => k + 1)
            reloadTeams()
          }}
        />
      </div>

      <RepoSyncTable teams={teams} refreshSignal={syncRefreshSignal} onTeamsChanged={reloadTeams} />

      {teams.length > 0 && (
        <div className="mt-4 border-t border-slate-100 pt-3">
          <div className="mb-2 flex items-center justify-between">
            <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400">
              Teams overview
              <InfoTooltip text="Every team currently defined — either created manually above or imported from a GitHub org — with how many repos each one has mapped. Updates automatically when you assign a repo; use Refresh if you ever need to force it." />
            </p>
            <button onClick={reloadTeams} className="text-xs font-medium text-slate-500 hover:text-slate-900">
              Refresh
            </button>
          </div>
          <ul className="flex flex-wrap gap-2">
            {teams.map((t) => (
              <li key={t.id} className="flex items-center gap-1.5 rounded bg-slate-100 px-2 py-1 text-xs text-slate-600">
                <span>
                  {t.name} · {t.repoCount} repo{t.repoCount === 1 ? '' : 's'}
                </span>
                <button
                  onClick={() => handleDeleteTeam(t)}
                  disabled={deletingId === t.id}
                  title="Delete team"
                  className="text-slate-400 hover:text-red-600 disabled:opacity-50"
                >
                  {deletingId === t.id ? '…' : '×'}
                </button>
              </li>
            ))}
          </ul>
          {deleteError && <p className="mt-2 text-xs text-red-600">{deleteError}</p>}
        </div>
      )}
    </div>
  )
}

function AuditLogPanel() {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchAuditLog(20)
      .then((e) => {
        if (!cancelled) setEntries(e)
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-3 flex items-center gap-2">
        <p className="text-sm text-slate-500">Audit log</p>
        <InfoTooltip text="Every configuration change, access grant, and data export — an append-only, admin-only record kept for 12+ months (BRD auditability requirement)." />
        <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">Live</span>
      </div>
      {error && <p className="text-sm text-red-600">Could not load audit log: {error}</p>}
      {!error && !entries && <p className="text-sm text-slate-400">Loading…</p>}
      {!error && entries && entries.length === 0 && (
        <p className="text-sm text-slate-400">No audit entries yet.</p>
      )}
      {!error && entries && entries.length > 0 && (
        <ul className="space-y-2 text-sm">
          {entries.map((entry) => (
            <li key={entry.id} className="border-b border-slate-100 pb-2 last:border-0">
              <p>
                <span className="font-medium">{entry.actorEmail ?? 'system'}</span>{' '}
                <span className="text-slate-500">{entry.action}</span>{' '}
                <span className="text-slate-600">
                  → {entry.targetType}
                  {entry.targetId ? `: ${entry.targetId}` : ''}
                </span>
              </p>
              <p className="text-xs text-slate-400">{formatTimestamp(entry.occurredAt)}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function roleBadge(role: Role) {
  if (role === 'ADMIN') return 'bg-purple-100 text-purple-700'
  if (role === 'MANAGER') return 'bg-blue-100 text-blue-700'
  if (role === 'ENG_LEADER') return 'bg-indigo-100 text-indigo-700'
  if (role === 'FINANCE_READONLY') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}

function roleNeedsTeam(role: Role) {
  // Only MANAGER is actually pinned to a team server-side (see api-core's ScopeResolver) —
  // ADMIN/ENG_LEADER/FINANCE_READONLY are org-wide by role regardless of team_id, and IC has
  // no metrics route at all yet. A team can still be set on other roles for record-keeping,
  // but it won't change what they can see.
  return role === 'MANAGER'
}

function roleNeedsGithub(role: Role) {
  // Only IC's Personal Activity tab reads github_login (see api-core's PersonalQueryService) —
  // it's how "self only" resolves to an actual staging.pull_request_state author/reviewer.
  return role === 'IC'
}

function NewUserForm({ teams, onCreated }: { teams: Team[]; onCreated: (u: AdminUser) => void }) {
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [role, setRole] = useState<Role>('IC')
  const [teamId, setTeamId] = useState('')
  const [githubLogin, setGithubLogin] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!email.trim() || !displayName.trim()) {
      setError('Email and name are required.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const created = await createAdminUser(
        email.trim(),
        displayName.trim(),
        role,
        roleNeedsTeam(role) ? teamId || null : null,
        roleNeedsGithub(role) ? githubLogin.trim() || null : null,
      )
      onCreated(created)
      setEmail('')
      setDisplayName('')
      setRole('IC')
      setTeamId('')
      setGithubLogin('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create user.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mb-4 flex flex-wrap items-end gap-2 border-b border-slate-100 pb-4">
      <div>
        <label className="mb-1 block text-xs text-slate-400">Email</label>
        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="name@company.com"
          className="rounded-md border border-slate-200 px-2 py-1 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs text-slate-400">Name</label>
        <input
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder="Full name"
          className="rounded-md border border-slate-200 px-2 py-1 text-sm"
        />
      </div>
      <div>
        <label className="mb-1 block text-xs text-slate-400">Role</label>
        <select
          value={role}
          onChange={(e) => setRole(e.target.value as Role)}
          className="rounded-md border border-slate-200 px-2 py-1 text-sm"
        >
          {ROLES.map((r) => (
            <option key={r} value={r}>
              {r}
            </option>
          ))}
        </select>
      </div>
      {roleNeedsTeam(role) && (
        <div>
          <label className="mb-1 block text-xs text-slate-400">Team</label>
          <select
            value={teamId}
            onChange={(e) => setTeamId(e.target.value)}
            className="rounded-md border border-slate-200 px-2 py-1 text-sm"
          >
            <option value="">Select a team…</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </div>
      )}
      {roleNeedsGithub(role) && (
        <div>
          <label className="mb-1 block text-xs text-slate-400">GitHub username</label>
          <input
            value={githubLogin}
            onChange={(e) => setGithubLogin(e.target.value)}
            placeholder="octocat"
            className="rounded-md border border-slate-200 px-2 py-1 text-sm"
          />
        </div>
      )}
      <button
        type="submit"
        disabled={busy}
        className="rounded-md bg-slate-900 px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
      >
        {busy ? 'Adding…' : 'Add user'}
      </button>
      {error && <p className="w-full text-sm text-red-600">{error}</p>}
    </form>
  )
}

function UserRow({ user, teams, onChanged }: { user: AdminUser; teams: Team[]; onChanged: (u: AdminUser) => void }) {
  const [editing, setEditing] = useState(false)
  const [role, setRole] = useState<Role>(user.role)
  const [teamId, setTeamId] = useState(user.teamId ?? '')
  const [githubLogin, setGithubLogin] = useState(user.githubLogin ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function startEditing() {
    setRole(user.role)
    setTeamId(user.teamId ?? '')
    setGithubLogin(user.githubLogin ?? '')
    setEditing(true)
  }

  async function save() {
    setBusy(true)
    setError(null)
    try {
      let updated = await updateAdminUserRole(user.id, role, roleNeedsTeam(role) ? teamId || null : null)
      if (roleNeedsGithub(role)) {
        updated = await updateAdminUserGithubLogin(user.id, githubLogin.trim() || null)
      }
      onChanged(updated)
      setEditing(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update user.')
    } finally {
      setBusy(false)
    }
  }

  async function toggleActive() {
    setBusy(true)
    setError(null)
    try {
      const updated = await setAdminUserActive(user.id, !user.active)
      onChanged(updated)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update status.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <tr className={`border-b border-slate-100 last:border-0 ${user.active ? '' : 'opacity-50'}`}>
      <td className="py-2 pr-4">
        <p className="font-medium">{user.displayName}</p>
        <p className="text-xs text-slate-400">{user.email}</p>
      </td>
      <td className="py-2 pr-4">
        {editing ? (
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as Role)}
            className="rounded-md border border-slate-200 px-2 py-1 text-xs"
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        ) : (
          <span className={`rounded px-2 py-0.5 text-xs font-medium ${roleBadge(user.role)}`}>{user.role}</span>
        )}
      </td>
      <td className="py-2 pr-4 text-slate-600">
        {editing && roleNeedsTeam(role) ? (
          <select
            value={teamId}
            onChange={(e) => setTeamId(e.target.value)}
            className="rounded-md border border-slate-200 px-2 py-1 text-xs"
          >
            <option value="">No team</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        ) : (
          user.teamName ?? (roleNeedsTeam(user.role) ? '— unassigned —' : '—')
        )}
      </td>
      <td className="py-2 pr-4 text-slate-600">
        {editing && roleNeedsGithub(role) ? (
          <input
            value={githubLogin}
            onChange={(e) => setGithubLogin(e.target.value)}
            placeholder="octocat"
            className="w-28 rounded-md border border-slate-200 px-2 py-1 text-xs"
          />
        ) : (
          user.githubLogin ?? (roleNeedsGithub(user.role) ? '— unlinked —' : '—')
        )}
      </td>
      <td className="py-2 pr-4 text-slate-600">{formatTimestamp(user.lastLoginAt)}</td>
      <td className="py-2 text-right">
        {editing ? (
          <div className="flex justify-end gap-2">
            <button onClick={save} disabled={busy} className="text-xs font-medium text-emerald-600 hover:text-emerald-700">
              Save
            </button>
            <button onClick={() => setEditing(false)} disabled={busy} className="text-xs text-slate-400 hover:text-slate-600">
              Cancel
            </button>
          </div>
        ) : (
          <div className="flex justify-end gap-3">
            <button onClick={startEditing} className="text-xs font-medium text-slate-500 hover:text-slate-900">
              Edit
            </button>
            <button onClick={toggleActive} disabled={busy} className="text-xs font-medium text-slate-500 hover:text-red-600">
              {user.active ? 'Deactivate' : 'Reactivate'}
            </button>
          </div>
        )}
        {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
      </td>
    </tr>
  )
}

function UsersPanel() {
  const [users, setUsers] = useState<AdminUser[] | null>(null)
  const [teams, setTeams] = useState<Team[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([fetchAdminUsers(), fetchTeams()])
      .then(([u, t]) => {
        if (!cancelled) {
          setUsers(u)
          setTeams(t)
        }
      })
      .catch((e: Error) => {
        if (!cancelled) setError(e.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  function upsert(updated: AdminUser) {
    setUsers((prev) => {
      if (!prev) return [updated]
      const exists = prev.some((u) => u.id === updated.id)
      return exists ? prev.map((u) => (u.id === updated.id ? updated : u)) : [...prev, updated].sort((a, b) => a.email.localeCompare(b.email))
    })
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-3 flex items-center gap-2">
        <p className="text-sm text-slate-500">Users &amp; role assignments (RBAC)</p>
        <InfoTooltip text="The real access-control record: sign-in looks a user up here for their role and team. MANAGER accounts are pinned server-side to the team assigned below; every other role sees data by role, not by team." />
        <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">Live</span>
      </div>
      <p className="mb-3 text-xs text-slate-400">
        This is the real access-control record: sign-in (the dev-token bridge, ADR-0004) looks a user up here for
        their role and team — it's no longer self-declared. MANAGER accounts are pinned server-side to the team
        assigned below; every other role sees data by role, not by team.
      </p>

      {error && <p className="text-sm text-red-600">Could not load users: {error}</p>}
      {!error && !users && <p className="text-sm text-slate-400">Loading…</p>}

      {!error && users && (
        <>
          <NewUserForm teams={teams} onCreated={upsert} />
          {users.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-400">No users yet — add the first one above.</p>
          ) : (
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400">
                  <th className="pb-2 pr-4">User</th>
                  <th className="pb-2 pr-4">Role</th>
                  <th className="pb-2 pr-4">Team</th>
                  <th className="pb-2 pr-4">GitHub</th>
                  <th className="pb-2 pr-4">Last login</th>
                  <th className="pb-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <UserRow key={u.id} user={u} teams={teams} onChanged={upsert} />
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  )
}

export default function Admin() {
  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <h2 className="text-xl font-semibold">Admin & Access Console</h2>
      </div>
      <p className="mb-6 text-sm text-slate-500">Connector health, role assignments, and the audit trail (12+ month retention)</p>

      <ConnectorsPanel />

      <div className="mt-4">
        <RepoTeamsPanel />
      </div>

      <div className="mt-4">
        <UsersPanel />
      </div>

      <div className="mt-4">
        <AuditLogPanel />
      </div>
    </section>
  )
}
