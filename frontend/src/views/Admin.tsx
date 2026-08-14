import { useEffect, useState } from 'react'
import { admin } from '../mock/mockData'
import { fetchAdminConnectors, fetchAuditLog, type AuditEntry, type ConnectorHealth } from '../api'

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

export default function Admin() {
  const { roles } = admin

  return (
    <section>
      <div className="mb-1 flex items-center gap-2">
        <h2 className="text-xl font-semibold">Admin & Access Console</h2>
      </div>
      <p className="mb-6 text-sm text-slate-500">Connector health, role assignments, and the audit trail (12+ month retention)</p>

      <ConnectorsPanel />

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="mb-3 flex items-center gap-2">
            <p className="text-sm text-slate-500">Role assignments (RBAC)</p>
            <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">Demo data</span>
          </div>
          <p className="mb-3 text-xs text-slate-400">
            User directory and role management ship with the OIDC login flow (ADR-0004); the dev-token bridge
            issues roles per session and does not persist assignments yet.
          </p>
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-400">
                <th className="pb-2 pr-4">User</th>
                <th className="pb-2 pr-4">Role</th>
                <th className="pb-2">Scope</th>
              </tr>
            </thead>
            <tbody>
              {roles.map((r) => (
                <tr key={r.email} className="border-b border-slate-100 last:border-0">
                  <td className="py-2 pr-4">{r.email}</td>
                  <td className="py-2 pr-4 text-slate-600">{r.role}</td>
                  <td className="py-2 text-slate-600">{r.scope}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <AuditLogPanel />
      </div>
    </section>
  )
}
