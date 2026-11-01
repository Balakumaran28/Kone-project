import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  getQueuedActions,
  removeQueuedAction,
  clearSyncedActions,
  syncSingleAction,
  syncAllPendingActions,
} from '../services/offlineQueue';
import {
  fetchMyNotifications,
  markNotificationAsRead,
  fetchOutboxSummary,
  fetchOutboxItems,
} from '../services/api';

export default function OfflineQueuePage() {
  const { user, token, isAuthenticated } = useAuth();

  // Queue state
  const [actions, setActions] = useState([]);
  const [filterStatus, setFilterStatus] = useState('ALL');
  const [syncingAll, setSyncingAll] = useState(false);
  const [syncProgress, setSyncProgress] = useState(null);
  const [activeTab, setActiveTab] = useState('queue'); // 'queue', 'notifications', 'outbox'

  // Notifications state
  const [notifications, setNotifications] = useState([]);
  const [loadingNotifs, setLoadingNotifs] = useState(false);

  // Outbox state (staff only)
  const [outboxSummary, setOutboxSummary] = useState(null);
  const [outboxItems, setOutboxItems] = useState([]);
  const [loadingOutbox, setLoadingOutbox] = useState(false);

  const [message, setMessage] = useState(null);

  const isStaff = user?.role === 'BUILDING_MANAGER' ||
                  user?.role === 'SECURITY_STAFF' ||
                  user?.role === 'ADMINISTRATOR' ||
                  user?.role === 'COMPANY_SUPERVISOR';

  // Load offline queue actions
  const loadActions = useCallback(async () => {
    try {
      const userEmail = user?.email || null;
      const items = await getQueuedActions(userEmail);
      setActions(items);
    } catch (err) {
      console.error('Failed to load offline actions:', err);
    }
  }, [user]);

  // Load in-app notifications
  const loadNotifications = useCallback(async () => {
    if (!token || !isAuthenticated) return;
    setLoadingNotifs(true);
    try {
      const list = await fetchMyNotifications(token);
      setNotifications(list);
    } catch (err) {
      console.error('Failed to load notifications:', err);
    } finally {
      setLoadingNotifs(false);
    }
  }, [token, isAuthenticated]);

  // Load outbox summary and items for staff
  const loadOutboxData = useCallback(async () => {
    if (!token || !isStaff) return;
    setLoadingOutbox(true);
    try {
      const [summary, pageRes] = await Promise.all([
        fetchOutboxSummary(token),
        fetchOutboxItems(token, { page: 0, size: 25 }),
      ]);
      setOutboxSummary(summary);
      setOutboxItems(pageRes.content || []);
    } catch (err) {
      console.error('Failed to load outbox data:', err);
    } finally {
      setLoadingOutbox(false);
    }
  }, [token, isStaff]);

  useEffect(() => {
    loadActions();
    if (isAuthenticated) {
      loadNotifications();
    }
    if (isStaff) {
      loadOutboxData();
    }
  }, [loadActions, loadNotifications, loadOutboxData, isAuthenticated, isStaff]);

  const handleSyncAll = async () => {
    if (!token) {
      setMessage({ type: 'warning', text: 'You must be signed in to synchronize queued actions.' });
      return;
    }
    setSyncingAll(true);
    setSyncProgress('Initiating batch synchronization with local server...');
    try {
      const result = await syncAllPendingActions(token, (current, total) => {
        setSyncProgress(`Synchronizing action ${current} of ${total}...`);
      });
      setMessage({
        type: 'success',
        text: `Batch sync complete: ${result.synced} processed, ${result.failed} failed.`,
      });
      await loadActions();
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Sync failed.' });
    } finally {
      setSyncingAll(false);
      setSyncProgress(null);
    }
  };

  const handleSyncSingle = async (item) => {
    if (!token) {
      setMessage({ type: 'warning', text: 'Please sign in to sync.' });
      return;
    }
    try {
      await syncSingleAction(item, token);
      setMessage({ type: 'success', text: `Synchronized action #${item.id}` });
      await loadActions();
    } catch (err) {
      setMessage({ type: 'error', text: `Failed: ${err.message}` });
      await loadActions();
    }
  };

  const handleRemove = async (id) => {
    await removeQueuedAction(id);
    await loadActions();
  };

  const handleClearSynced = async () => {
    await clearSyncedActions();
    await loadActions();
  };

  const handleMarkRead = async (notifId) => {
    try {
      await markNotificationAsRead(token, notifId);
      setNotifications((prev) =>
        prev.map((n) => (n.id === notifId ? { ...n, isRead: true } : n))
      );
    } catch (err) {
      console.error('Failed to mark notification as read:', err);
    }
  };

  const pendingCount = actions.filter((a) => a.syncStatus === 'PENDING' || a.syncStatus === 'FAILED').length;
  const unreadNotifsCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div>
      {/* Page Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>
            <span>Offline Action Queue & Delivery Telemetry</span>
            <span className="badge badge-info">INDEXEDDB PERSISTED</span>
          </h1>
          <p className="page-subtitle">
            Local browser buffer guaranteeing zero data loss during building network disconnects
          </p>
        </div>
        <div className="page-actions">
          <button onClick={loadActions} className="btn btn-secondary btn-sm">
            ↻ Refresh Queue
          </button>
        </div>
      </div>

      {message && (
        <div
          style={{
            padding: '0.85rem 1rem',
            background: message.type === 'error' ? 'var(--danger-bg)' : message.type === 'warning' ? 'var(--warning-bg)' : 'var(--success-bg)',
            border: `1px solid ${message.type === 'error' ? 'var(--danger-border)' : message.type === 'warning' ? 'var(--warning-border)' : 'var(--success-border)'}`,
            borderRadius: 'var(--radius-sm)',
            color: message.type === 'error' ? 'var(--danger-text)' : message.type === 'warning' ? 'var(--warning-text)' : 'var(--success-text)',
            fontSize: '0.85rem',
            marginBottom: '1.25rem',
          }}
        >
          {message.text}
        </div>
      )}

      {syncProgress && (
        <div
          style={{
            padding: '0.75rem 1rem',
            background: 'var(--info-bg)',
            border: '1px solid var(--info-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--info-text)',
            fontSize: '0.82rem',
            marginBottom: '1rem',
          }}
        >
          ⚡ {syncProgress}
        </div>
      )}

      {/* Tabs */}
      <div className="tab-bar">
        <button
          className={`tab-btn ${activeTab === 'queue' ? 'active' : ''}`}
          onClick={() => setActiveTab('queue')}
        >
          <span>📥 Client Action Queue</span>
          {pendingCount > 0 && (
            <span className="badge badge-warning" style={{ fontSize: '0.65rem' }}>
              {pendingCount}
            </span>
          )}
        </button>

        {isAuthenticated && (
          <button
            className={`tab-btn ${activeTab === 'notifications' ? 'active' : ''}`}
            onClick={() => setActiveTab('notifications')}
          >
            <span>🔔 In-App Alerts</span>
            {unreadNotifsCount > 0 && (
              <span className="badge badge-critical" style={{ fontSize: '0.65rem' }}>
                {unreadNotifsCount}
              </span>
            )}
          </button>
        )}

        {isStaff && (
          <button
            className={`tab-btn ${activeTab === 'outbox' ? 'active' : ''}`}
            onClick={() => setActiveTab('outbox')}
          >
            <span>📡 Server Durable Outbox</span>
            {outboxSummary && (
              <span className="badge badge-neutral" style={{ fontSize: '0.65rem' }}>
                {outboxSummary.totalNotifications}
              </span>
            )}
          </button>
        )}
      </div>

      {/* TAB 1: QUEUE */}
      {activeTab === 'queue' && (
        <div>
          <div className="metric-grid">
            <div className="metric-card">
              <span className="metric-label">Enqueued Actions</span>
              <div className="metric-value">{actions.length}</div>
              <div className="metric-subtext">Total client mutations</div>
            </div>
            <div className="metric-card warning">
              <span className="metric-label">Pending Sync</span>
              <div className="metric-value" style={{ color: '#d97706' }}>{pendingCount}</div>
              <div className="metric-subtext">Awaiting server transmission</div>
            </div>
            <div className="metric-card success">
              <span className="metric-label">Successfully Synced</span>
              <div className="metric-value" style={{ color: '#15803d' }}>
                {actions.filter((a) => a.syncStatus === 'SYNCED').length}
              </div>
              <div className="metric-subtext">Committed to local server</div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.5rem' }}>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <select
                className="form-select"
                value={filterStatus}
                onChange={(e) => setFilterStatus(e.target.value)}
                style={{ fontSize: '0.8rem', padding: '0.35rem 0.6rem', width: 'auto' }}
              >
                <option value="ALL">All Actions</option>
                <option value="PENDING">Pending Only</option>
                <option value="FAILED">Failed Only</option>
                <option value="SYNCED">Synced Only</option>
              </select>
            </div>

            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                onClick={handleSyncAll}
                disabled={syncingAll || pendingCount === 0}
                className="btn btn-primary btn-sm"
              >
                {syncingAll ? 'Replaying...' : '⚡ Replay Pending Actions'}
              </button>
              <button
                onClick={handleClearSynced}
                className="btn btn-ghost btn-sm"
              >
                Clear Synced Records
              </button>
            </div>
          </div>

          {actions.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">📦</div>
              <h3 className="empty-title">Queue is Empty</h3>
              <p className="empty-desc">All incident reports and triage actions are synchronized with the local server.</p>
            </div>
          ) : (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Action Type</th>
                    <th>Idempotency Key</th>
                    <th>Created</th>
                    <th>Status</th>
                    <th>Retries</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {actions
                    .filter((a) => filterStatus === 'ALL' || a.syncStatus === filterStatus)
                    .map((item) => (
                      <tr key={item.id}>
                        <td>
                          <span style={{ fontWeight: 600 }}>{item.actionType}</span>
                        </td>
                        <td>
                          <code style={{ fontSize: '0.74rem' }}>{item.idempotencyKey.slice(0, 16)}...</code>
                        </td>
                        <td style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                          {new Date(item.createdAt).toLocaleTimeString()}
                        </td>
                        <td>
                          <span className={`badge ${item.syncStatus === 'SYNCED' ? 'badge-success' : item.syncStatus === 'FAILED' ? 'badge-critical' : 'badge-warning'}`}>
                            {item.syncStatus}
                          </span>
                        </td>
                        <td>{item.retryCount || 0}</td>
                        <td>
                          <div style={{ display: 'flex', gap: '0.3rem' }}>
                            {item.syncStatus !== 'SYNCED' && (
                              <button
                                onClick={() => handleSyncSingle(item)}
                                className="btn btn-primary btn-sm"
                                style={{ fontSize: '0.72rem', padding: '0.2rem 0.5rem' }}
                              >
                                Replay
                              </button>
                            )}
                            <button
                              onClick={() => handleRemove(item.id)}
                              className="btn btn-ghost btn-sm"
                              style={{ fontSize: '0.72rem', padding: '0.2rem 0.5rem' }}
                            >
                              Remove
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
      )}

      {/* TAB 2: IN-APP NOTIFICATIONS */}
      {activeTab === 'notifications' && (
        <div>
          {notifications.length === 0 ? (
            <div className="empty-state">
              <div className="empty-icon">🔔</div>
              <h3 className="empty-title">No Notifications</h3>
              <p className="empty-desc">You're caught up with all operational alerts.</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {notifications.map((notif) => (
                <div
                  key={notif.id}
                  className="card"
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    borderLeft: notif.isRead ? '3px solid var(--border-subtle)' : '3px solid var(--elevator-blue-primary)',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <strong style={{ fontSize: '0.88rem', color: 'var(--text-primary)' }}>{notif.title}</strong>
                      {!notif.isRead && <span className="badge badge-info" style={{ fontSize: '0.62rem' }}>NEW</span>}
                    </div>
                    <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', margin: '0.2rem 0 0' }}>
                      {notif.message}
                    </p>
                    <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
                      {new Date(notif.createdAt).toLocaleString()}
                    </span>
                  </div>

                  {!notif.isRead && (
                    <button
                      onClick={() => handleMarkRead(notif.id)}
                      className="btn btn-secondary btn-sm"
                      style={{ fontSize: '0.75rem' }}
                    >
                      Mark Read
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: SERVER OUTBOX (STAFF) */}
      {activeTab === 'outbox' && isStaff && (
        <div>
          {outboxSummary && (
            <div className="metric-grid" style={{ marginBottom: '1.25rem' }}>
              <div className="metric-card">
                <span className="metric-label">Total Outbox Events</span>
                <div className="metric-value">{outboxSummary.totalNotifications}</div>
              </div>
              <div className="metric-card success">
                <span className="metric-label">Delivered</span>
                <div className="metric-value" style={{ color: '#15803d' }}>{outboxSummary.deliveredCount}</div>
              </div>
              <div className="metric-card warning">
                <span className="metric-label">Pending Broadcast</span>
                <div className="metric-value" style={{ color: '#d97706' }}>{outboxSummary.pendingCount}</div>
              </div>
              <div className="metric-card critical">
                <span className="metric-label">Permanent Failures</span>
                <div className="metric-value" style={{ color: '#dc2626' }}>{outboxSummary.failedCount}</div>
              </div>
            </div>
          )}

          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Event ID</th>
                  <th>Recipient / Role</th>
                  <th>Channel</th>
                  <th>Subject</th>
                  <th>Delivery Status</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {outboxItems.map((item) => (
                  <tr key={item.id}>
                    <td><code>#{item.id}</code></td>
                    <td>{item.recipientRole || item.recipientIdentifier}</td>
                    <td><span className="badge badge-neutral">{item.channel}</span></td>
                    <td>{item.subject}</td>
                    <td>
                      <span className={`badge ${item.deliveryStatus === 'DELIVERED' ? 'badge-success' : item.deliveryStatus === 'FAILED' ? 'badge-critical' : 'badge-warning'}`}>
                        {item.deliveryStatus}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>
                      {new Date(item.createdAt).toLocaleTimeString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
