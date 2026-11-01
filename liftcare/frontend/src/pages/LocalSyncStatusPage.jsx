import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getLocalSyncStatus, triggerLocalSync, toggleSimulationOffline } from '../services/api';

export default function LocalSyncStatusPage() {
  const { token, user } = useAuth();
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const fetchStatus = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getLocalSyncStatus(token);
      setStatus(data);
    } catch (err) {
      setError(err.message || 'Failed to load local sync status');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatus();
  }, [token]);

  const handleTriggerSync = async () => {
    try {
      setSyncing(true);
      setError(null);
      setSuccessMsg(null);
      const res = await triggerLocalSync(token);
      if (res.status === 'SUCCESS') {
        setSuccessMsg(`Successfully synchronized batch ${res.batchId} (${res.processedCount} processed, ${res.duplicateCount} deduplicated)`);
      } else if (res.status === 'OFFLINE') {
        setError('Cloud coordination server is unreachable. Changes remain buffered safely in local persistent outbox.');
      } else {
        setSuccessMsg(`Sync batch executed with status: ${res.status}`);
      }
      await fetchStatus();
    } catch (err) {
      setError(err.message || 'Synchronization request failed');
    } finally {
      setSyncing(false);
    }
  };

  const handleToggleSimulation = async (offline) => {
    try {
      await toggleSimulationOffline(token, offline);
      await fetchStatus();
    } catch (err) {
      setError(err.message);
    }
  };

  const isManagerOrAdmin = user && (user.role === 'ADMINISTRATOR' || user.role === 'BUILDING_MANAGER');
  const isAdmin = user && user.role === 'ADMINISTRATOR';

  return (
    <div>
      {/* Page Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>
            <span>Cloud Coordination & Sync Telemetry</span>
            <span className={`badge ${status?.cloudConnectionStatus === 'ONLINE' ? 'badge-success' : 'badge-warning'}`}>
              {status?.cloudConnectionStatus || 'INITIALIZING'}
            </span>
          </h1>
          <p className="page-subtitle">
            Local building server synchronization bridge to central cloud coordinator &bull; Building: <strong>{status?.buildingCode || 'BLD-ALPHA'}</strong>
          </p>
        </div>
        <div className="page-actions">
          <button
            onClick={fetchStatus}
            disabled={loading || syncing}
            className="btn btn-secondary btn-sm"
          >
            {loading ? 'Probing...' : '↻ Refresh Status'}
          </button>
        </div>
      </div>

      {/* Autonomous Local Guarantee Banner */}
      <div
        style={{
          background: 'var(--success-bg)',
          border: '1px solid var(--success-border)',
          borderRadius: 'var(--radius-md)',
          padding: '1.25rem',
          marginBottom: '1.5rem',
          display: 'flex',
          alignItems: 'center',
          gap: '1rem',
        }}
      >
        <span style={{ fontSize: '1.75rem' }}>🛡️</span>
        <div>
          <strong style={{ color: '#15803d', fontSize: '0.98rem', display: 'block' }}>
            Offline-First Autonomy Guarantee Active
          </strong>
          <p style={{ margin: '0.2rem 0 0', color: 'var(--text-secondary)', fontSize: '0.84rem', lineHeight: 1.45 }}>
            The local building server and on-premise database operate with complete independence. Incident reporting, triage, history audit logs, and SLA monitoring proceed normally even during prolonged internet network outages.
          </p>
        </div>
      </div>

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

      {successMsg && (
        <div
          style={{
            padding: '0.85rem 1rem',
            background: 'var(--success-bg)',
            border: '1px solid var(--success-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--success-text)',
            fontSize: '0.85rem',
            marginBottom: '1.25rem',
          }}
        >
          ✓ {successMsg}
        </div>
      )}

      {loading && !status ? (
        <div className="card" style={{ textAlign: 'center', padding: '3.5rem' }}>
          <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Querying local node synchronization checkpoints...
          </div>
        </div>
      ) : status ? (
        <div className="metric-grid" style={{ marginBottom: '1.5rem' }}>
          {/* Card 1: Cloud Link State */}
          <div className="metric-card">
            <span className="metric-label">Cloud Coordinator Link</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginTop: '0.35rem' }}>
              <span
                style={{
                  width: '12px',
                  height: '12px',
                  borderRadius: '50%',
                  background: status.cloudConnectionStatus === 'ONLINE' ? '#10b981' : '#f59e0b',
                  boxShadow: status.cloudConnectionStatus === 'ONLINE' ? '0 0 8px rgba(16, 185, 129, 0.7)' : '0 0 8px rgba(245, 158, 11, 0.7)',
                }}
              />
              <span style={{ fontSize: '1.3rem', fontWeight: 800, color: 'var(--text-primary)' }}>
                {status.cloudConnectionStatus}
              </span>
            </div>
            <div className="metric-subtext">Node: <code>{status.nodeId || 'building-local-node-01'}</code></div>
          </div>

          {/* Card 2: Queue Depth */}
          <div className="metric-card">
            <span className="metric-label">Durable Outbox Queue</span>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '1rem', marginTop: '0.35rem' }}>
              <div>
                <span className="metric-value" style={{ color: 'var(--elevator-blue-primary)' }}>
                  {status.pendingEventsCount}
                </span>
                <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-muted)' }}>Pending Sync</span>
              </div>
              <div>
                <span className="metric-value" style={{ color: status.failedEventsCount > 0 ? '#dc2626' : '#15803d' }}>
                  {status.failedEventsCount}
                </span>
                <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-muted)' }}>Failed Retries</span>
              </div>
            </div>
          </div>

          {/* Card 3: Last Synchronization */}
          <div className="metric-card">
            <span className="metric-label">Last Successful Sync</span>
            <div style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--text-primary)', marginTop: '0.5rem' }}>
              {status.lastSuccessfulSyncAt
                ? new Date(status.lastSuccessfulSyncAt).toLocaleString()
                : 'Initial sync pending'}
            </div>
            <div className="metric-subtext">
              {status.lastSyncAttemptAt
                ? `Last attempt: ${new Date(status.lastSyncAttemptAt).toLocaleTimeString()}`
                : 'No sync attempts logged'}
            </div>
          </div>
        </div>
      ) : null}

      {/* Sync Error Notice */}
      {status?.lastSyncError && (
        <div
          style={{
            background: 'var(--warning-bg)',
            border: '1px solid var(--warning-border)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem',
            marginBottom: '1.5rem',
          }}
        >
          <strong style={{ color: 'var(--warning-text)', fontSize: '0.85rem' }}>Recent Synchronization Error:</strong>
          <p style={{ margin: '0.25rem 0 0', color: '#92400e', fontSize: '0.82rem' }}>
            {status.lastSyncError}
          </p>
        </div>
      )}

      {/* Actions and Controls */}
      {isManagerOrAdmin && (
        <div className="card" style={{ marginBottom: '1.5rem' }}>
          <div className="card-header">
            <div>
              <h2 className="card-title">Synchronization Controls</h2>
              <div className="card-subtitle">Trigger outbound synchronization batches or simulate network conditions</div>
            </div>
            <span className="badge badge-info">STAFF OPERATIONAL</span>
          </div>

          <div style={{ display: 'flex', gap: '0.85rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <button
              onClick={handleTriggerSync}
              disabled={syncing || loading}
              className="btn btn-primary"
            >
              {syncing ? 'Synchronizing with Cloud...' : '⚡ Sync Outbox Now'}
            </button>

            {isAdmin && (
              <div style={{ display: 'flex', gap: '0.6rem', alignItems: 'center', marginLeft: 'auto', flexWrap: 'wrap' }}>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>Diagnostic Simulation:</span>
                <button
                  onClick={() => handleToggleSimulation(true)}
                  className="btn btn-warning btn-sm"
                  title="Simulate cloud coordination network disconnect"
                >
                  Simulate Cloud Disconnect
                </button>
                <button
                  onClick={() => handleToggleSimulation(false)}
                  className="btn btn-success btn-sm"
                  title="Restore simulated cloud connection"
                >
                  Restore Cloud Connection
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
