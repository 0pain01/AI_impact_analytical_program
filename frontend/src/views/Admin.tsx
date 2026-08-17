import { useEffect, useState } from 'react'
import {
  createAdminUser,
  fetchAdminConnectors,
  fetchAdminUsers,
  fetchAuditLog,
  fetchTeams,
  setAdminUserActive,
  updateAdminUserGithubLogin,
  updateAdminUserRole,
  type AdminUser,
  type AuditEntry,
  type ConnectorHealth,
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
              <th className="pb-2">Last sync</th>
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
                <td className="py-2 text-slate-600">{formatTimestamp(c.lastSyncAt)}</td>
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

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <UsersPanel />
        <AuditLogPanel />
      </div>
    </section>
  )
}