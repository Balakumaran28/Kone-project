import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import {
  getCentralDashboardSummary,
  getCentralIncidents,
  getCentralConflicts,
  resolveCentralConflict
} from '../services/api';

export default function CentralDashboardPage() {
  const { token, user } = useAuth();
  const [summary, setSummary] = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [conflicts, setConflicts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedBuilding, setSelectedBuilding] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('');
  const [resolvingConflictId, setResolvingConflictId] = useState(null);
  const [resolutionAction, setResolutionAction] = useState('ACCEPTED_LOCAL');
  const [resolutionNotes, setResolutionNotes] = useState('');

  const fetchData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [sumData, incData, confData] = await Promise.all([
        getCentralDashboardSummary(token),
        getCentralIncidents(token, {
          buildingCode: selectedBuilding,
          severity: selectedSeverity,
          status: selectedStatus
        }),
        getCentralConflicts(token)
      ]);
      setSummary(sumData);
      setIncidents(incData);
      setConflicts(confData);
    } catch (err) {
      setError(err.message || 'Failed to fetch central dashboard data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [token, selectedBuilding, selectedSeverity, selectedStatus]);

  const handleResolveConflict = async (conflictId) => {
    try {
      await resolveCentralConflict(token, conflictId, {
        resolutionAction,
        resolutionNotes
      });
      setResolvingConflictId(null);
      setResolutionNotes('');
      await fetchData();
    } catch (err) {
      alert('Failed to resolve conflict: ' + err.message);
    }
  };

  return (
    <div>
      {/* Page Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>
            <span>Central Fleet & Multi-Building Coordinator</span>
            <span className="badge badge-info">CENTRAL CLOUD</span>
          </h1>
          <p className="page-subtitle">
            Aggregated fleet coordination and synchronized replica telemetry across registered buildings
          </p>
        </div>
        <div className="page-actions">
          <button
            onClick={fetchData}
            disabled={loading}
            className="btn btn-secondary btn-sm"
          >
            {loading ? 'Refreshing...' : '↻ Refresh Fleet'}
          </button>
        </div>
      </div>

      {/* Stale Telemetry Warning Banner */}
      {summary?.isStaleDataWarning && (
        <div
          style={{
            background: 'var(--warning-bg)',
            border: '1px solid var(--warning-border)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem',
            marginBottom: '1.5rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
          }}
        >
          <span style={{ fontSize: '1.25rem' }}>⚠️</span>
          <div>
            <strong style={{ color: 'var(--warning-text)', fontSize: '0.88rem' }}>
              Stale Synchronization Telemetry Detected
            </strong>
            <p style={{ margin: '0.15rem 0 0', color: '#92400e', fontSize: '0.82rem' }}>
              One or more buildings have not reported synchronization updates within standard heartbeat thresholds.
            </p>
          </div>
        </div>
      )}

      {error && (
        <div
          style={{
            padding: '0.85rem 1rem',
            background: 'var(--danger-bg)',
            border: '1px solid var(--danger-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--danger-text)',
            fontSize: '0.85rem',
            marginBottom: '1.25rem',
          }}
        >
          ⚠️ {error}
        </div>
      )}

      {/* Metric Cards */}
      <div className="metric-grid">
        <div className="metric-card">
          <span className="metric-label">Registered Complexes</span>
          <div className="metric-value" style={{ color: 'var(--text-primary)' }}>
            {summary?.totalRegisteredBuildings ?? 0}
          </div>
          <div className="metric-subtext">Active building nodes</div>
        </div>

        <div className="metric-card">
          <span className="metric-label">Synchronized Replicas</span>
          <div className="metric-value" style={{ color: 'var(--elevator-blue-primary)' }}>
            {summary?.totalSynchronizedIncidents ?? 0}
          </div>
          <div className="metric-subtext">Central replica tickets</div>
        </div>

        <div className="metric-card critical">
          <span className="metric-label">Active Entrapments</span>
          <div className="metric-value" style={{ color: summary?.activeEntrapmentsCount > 0 ? '#dc2626' : 'var(--text-primary)' }}>
            {summary?.activeEntrapmentsCount ?? 0}
          </div>
          <div className="metric-subtext">Fleet-wide passenger alerts</div>
        </div>

        <div className={`metric-card ${conflicts.length > 0 ? 'warning' : ''}`}>
          <span className="metric-label">Pending Conflicts</span>
          <div className="metric-value" style={{ color: conflicts.length > 0 ? '#d97706' : '#15803d' }}>
            {conflicts.length}
          </div>
          <div className="metric-subtext">Requires coordinator review</div>
        </div>
      </div>

      {/* Building Status Grid */}
      {summary?.buildings && summary.buildings.length > 0 && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <div className="card-header">
            <div>
              <h2 className="card-title">Connected Building Nodes</h2>
              <div className="card-subtitle">Last sync timestamps and replica health by building</div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1rem' }}>
            {summary.buildings.map((b) => (
              <div
                key={b.buildingCode}
                style={{
                  background: 'var(--bg-secondary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  padding: '1rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.4rem' }}>
                  <strong style={{ fontSize: '0.95rem', color: 'var(--text-primary)' }}>{b.name || b.buildingCode}</strong>
                  <span className={`badge ${b.syncStatus === 'HEALTHY' ? 'badge-success' : 'badge-warning'}`}>
                    {b.syncStatus || 'SYNCED'}
                  </span>
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                  Code: <code>{b.buildingCode}</code> &bull; Open Tickets: <strong style={{ color: 'var(--elevator-blue-primary)' }}>{b.openIncidentsCount ?? 0}</strong>
                </div>
                <div style={{ fontSize: '0.74rem', color: 'var(--text-muted)', marginTop: '0.35rem' }}>
                  Last synced: {b.lastSyncAt ? new Date(b.lastSyncAt).toLocaleString() : 'Never'}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Multi-Building Incident Replicas */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="card-header">
          <div>
            <h2 className="card-title">Aggregated Incident Replicas ({incidents.length})</h2>
            <div className="card-subtitle">Read-only synchronized replicas across all registered properties</div>
          </div>
        </div>

        {incidents.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">🌐</div>
            <h3 className="empty-title">No Incident Replicas</h3>
            <p className="empty-desc">No synchronized incident records matching the selected building or status.</p>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Ticket</th>
                  <th>Building</th>
                  <th>Elevator</th>
                  <th>Severity</th>
                  <th>Status</th>
                  <th>Last Sync</th>
                </tr>
              </thead>
              <tbody>
                {incidents.map((inc) => (
                  <tr key={inc.id}>
                    <td>
                      <span className="ticket-code">{inc.ticketNumber}</span>
                      {inc.entrapmentReported && (
                        <span className="badge badge-critical" style={{ marginLeft: '0.4rem', fontSize: '0.62rem' }}>
                          ENTRAPMENT
                        </span>
                      )}
                    </td>
                    <td>{inc.buildingCode}</td>
                    <td><code>{inc.elevatorCode}</code></td>
                    <td>
                      <span className={`badge ${inc.severity === 'CRITICAL' ? 'badge-critical' : inc.severity === 'HIGH' ? 'badge-warning' : 'badge-info'}`}>
                        {inc.severity}
                      </span>
                    </td>
                    <td>
                      <span className="badge badge-neutral">{inc.status}</span>
                    </td>
                    <td style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                      {inc.lastSynchronizedAt ? new Date(inc.lastSynchronizedAt).toLocaleTimeString() : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Conflict Resolution Section */}
      {conflicts.length > 0 && (
        <div className="card" style={{ borderLeft: '4px solid #f59e0b' }}>
          <div className="card-header">
            <div>
              <h2 className="card-title" style={{ color: '#92400e' }}>Synchronization Conflict Log ({conflicts.length})</h2>
              <div className="card-subtitle">Divergent edits detected during bidirectional synchronization</div>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {conflicts.map((conf) => (
              <div
                key={conf.id}
                style={{
                  background: 'var(--bg-secondary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  padding: '1rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <strong style={{ fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                    Conflict on Ticket: <code>{conf.ticketNumber}</code> ({conf.buildingCode})
                  </strong>
                  <span className="badge badge-warning">{conf.status}</span>
                </div>
                <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', margin: '0 0 0.75rem' }}>
                  {conf.conflictReason}
                </p>

                {resolvingConflictId === conf.id ? (
                  <div style={{ background: 'var(--bg-card)', padding: '0.85rem', borderRadius: 'var(--radius-sm)' }}>
                    <div className="form-group">
                      <label className="form-label">Resolution Strategy</label>
                      <select
                        className="form-select"
                        value={resolutionAction}
                        onChange={(e) => setResolutionAction(e.target.value)}
                      >
                        <option value="ACCEPTED_LOCAL">Accept Local Building State (Preserve Edge)</option>
                        <option value="ACCEPTED_CLOUD">Accept Central Coordinator State</option>
                        <option value="MANUAL_MERGE">Manual Supervisory Merge</option>
                      </select>
                    </div>

                    <div className="form-group">
                      <label className="form-label">Resolution Notes</label>
                      <textarea
                        className="form-textarea"
                        rows={2}
                        value={resolutionNotes}
                        onChange={(e) => setResolutionNotes(e.target.value)}
                        placeholder="Explain resolution rationale for audit log..."
                      />
                    </div>

                    <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => setResolvingConflictId(null)}
                      >
                        Cancel
                      </button>
                      <button
                        type="button"
                        className="btn btn-primary btn-sm"
                        onClick={() => handleResolveConflict(conf.id)}
                      >
                        Confirm Resolution
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    onClick={() => setResolvingConflictId(conf.id)}
                    className="btn btn-warning btn-sm"
                  >
                    Resolve Conflict &rarr;
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
