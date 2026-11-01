import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { fetchIncidents } from '../services/api';

export default function IncidentsPage() {
  const { user, token } = useAuth();

  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters
  const [statusFilter, setStatusFilter] = useState('');
  const [severityFilter, setSeverityFilter] = useState('');
  const [entrapmentOnly, setEntrapmentOnly] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const loadIncidents = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      const filters = {};
      if (statusFilter) filters.status = statusFilter;
      if (severityFilter) filters.severity = severityFilter;
      if (entrapmentOnly) filters.entrapmentOnly = true;

      const data = await fetchIncidents(token, filters);
      setIncidents(data);
    } catch (err) {
      setError(err.message || 'Unable to load incidents from local server.');
    } finally {
      setLoading(false);
    }
  }, [token, statusFilter, severityFilter, entrapmentOnly]);

  useEffect(() => {
    loadIncidents();
  }, [loadIncidents]);

  const filteredIncidents = incidents.filter((inc) => {
    const q = searchQuery.toLowerCase();
    return (
      inc.ticketNumber.toLowerCase().includes(q) ||
      (inc.buildingName && inc.buildingName.toLowerCase().includes(q)) ||
      (inc.elevatorCode && inc.elevatorCode.toLowerCase().includes(q)) ||
      (inc.description && inc.description.toLowerCase().includes(q)) ||
      (inc.issueCategoryName && inc.issueCategoryName.toLowerCase().includes(q))
    );
  });

  const getSeverityBadge = (sev) => {
    switch (sev) {
      case 'CRITICAL':
        return <span className="badge badge-critical">CRITICAL</span>;
      case 'HIGH':
        return <span className="badge badge-high">HIGH</span>;
      case 'MEDIUM':
        return <span className="badge badge-medium">MEDIUM</span>;
      case 'LOW':
      default:
        return <span className="badge badge-low">LOW</span>;
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'REPORTED':
        return <span className="badge badge-info">REPORTED</span>;
      case 'ACKNOWLEDGED':
        return <span className="badge badge-medium">ACKNOWLEDGED</span>;
      case 'ASSIGNED':
        return <span className="badge badge-info">ASSIGNED</span>;
      case 'IN_PROGRESS':
        return <span className="badge badge-warning">IN PROGRESS</span>;
      case 'RESOLVED':
        return <span className="badge badge-success">RESOLVED</span>;
      case 'CLOSED':
        return <span className="badge badge-neutral">CLOSED</span>;
      default:
        return <span className="badge badge-neutral">{status}</span>;
    }
  };

  const formatDate = (isoString) => {
    if (!isoString) return '—';
    try {
      const d = new Date(isoString);
      return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return isoString;
    }
  };

  return (
    <div>
      {/* Page Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>
            <span>Incident Queue & Triage</span>
            <span className="badge badge-neutral" style={{ fontSize: '0.75rem' }}>
              {filteredIncidents.length} Tickets
            </span>
          </h1>
          <p className="page-subtitle">
            {user?.role === 'RESIDENT'
              ? 'Elevator faults and emergency reports submitted by your account'
              : 'Local control room triage, technician assignments, and fault tracking'}
          </p>
        </div>
        <div className="page-actions">
          <Link to="/incidents/report" className="btn btn-danger btn-sm">
            <span>+</span> Report Incident
          </Link>
          <button onClick={loadIncidents} className="btn btn-secondary btn-sm" disabled={loading}>
            {loading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="filter-bar">
        <div className="filter-group" style={{ flex: '1 1 240px' }}>
          <input
            type="text"
            className="form-input"
            placeholder="Search tickets, shafts, categories..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ fontSize: '0.82rem', padding: '0.45rem 0.75rem' }}
          />
        </div>

        <div className="filter-group">
          <select
            className="form-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            style={{ fontSize: '0.82rem', padding: '0.45rem 0.75rem', width: 'auto' }}
          >
            <option value="">All Statuses</option>
            <option value="REPORTED">Reported</option>
            <option value="ACKNOWLEDGED">Acknowledged</option>
            <option value="ASSIGNED">Assigned</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </select>

          <select
            className="form-select"
            value={severityFilter}
            onChange={(e) => setSeverityFilter(e.target.value)}
            style={{ fontSize: '0.82rem', padding: '0.45rem 0.75rem', width: 'auto' }}
          >
            <option value="">All Severities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>

          <label
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              fontSize: '0.82rem',
              fontWeight: 600,
              color: entrapmentOnly ? '#b91c1c' : 'var(--text-secondary)',
              cursor: 'pointer',
              userSelect: 'none',
              padding: '0.45rem 0.65rem',
              background: entrapmentOnly ? '#fee2e2' : 'transparent',
              borderRadius: 'var(--radius-sm)',
              border: entrapmentOnly ? '1px solid #f87171' : '1px solid transparent',
            }}
          >
            <input
              type="checkbox"
              checked={entrapmentOnly}
              onChange={(e) => setEntrapmentOnly(e.target.checked)}
              style={{ cursor: 'pointer' }}
            />
            🚨 Entrapments Only
          </label>
        </div>
      </div>

      {error && (
        <div
          style={{
            padding: '0.75rem 1rem',
            background: 'var(--danger-bg)',
            border: '1px solid var(--danger-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--danger-text)',
            fontSize: '0.85rem',
            marginBottom: '1rem',
          }}
        >
          ⚠️ {error}
        </div>
      )}

      {/* Incident Data Table */}
      {loading ? (
        <div className="card" style={{ textAlign: 'center', padding: '3rem' }}>
          <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Retrieving incident records from local server database...
          </div>
        </div>
      ) : filteredIncidents.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">📋</div>
          <h3 className="empty-title">No Incident Tickets Found</h3>
          <p className="empty-desc">
            {searchQuery || statusFilter || severityFilter || entrapmentOnly
              ? 'No tickets match the current filter criteria. Try clearing some filters.'
              : 'All elevator shafts are running without open reported faults.'}
          </p>
          <Link to="/incidents/report" className="btn btn-primary btn-sm">
            Report New Incident
          </Link>
        </div>
      ) : (
        <div className="table-container">
          <table className="data-table">
            <thead>
              <tr>
                <th>Ticket ID</th>
                <th>Building & Elevator</th>
                <th>Issue Category</th>
                <th>Severity</th>
                <th>Status</th>
                <th>Reported</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredIncidents.map((inc) => (
                <tr key={inc.id}>
                  <td>
                    <Link
                      to={`/incidents/${inc.id}`}
                      className="ticket-code"
                      title="Inspect incident lifecycle and audit trail"
                    >
                      {inc.ticketNumber}
                    </Link>
                    {inc.entrapmentReported && (
                      <span
                        className="badge badge-critical"
                        style={{ marginLeft: '0.4rem', fontSize: '0.62rem' }}
                      >
                        ENTRAPMENT
                      </span>
                    )}
                  </td>
                  <td>
                    <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                      {inc.buildingName || inc.buildingCode || 'Building #' + inc.buildingId}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      Shaft: <code style={{ color: 'var(--elevator-blue-primary)', fontWeight: 600 }}>{inc.elevatorCode || '#' + inc.elevatorId}</code>
                    </div>
                  </td>
                  <td>
                    <span style={{ fontWeight: 500 }}>{inc.issueCategoryName || inc.issueCategoryCode}</span>
                    <div
                      style={{
                        fontSize: '0.74rem',
                        color: 'var(--text-secondary)',
                        maxWidth: '260px',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {inc.description}
                    </div>
                  </td>
                  <td>{getSeverityBadge(inc.severity)}</td>
                  <td>{getStatusBadge(inc.status)}</td>
                  <td style={{ fontSize: '0.78rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                    {formatDate(inc.reportedAt)}
                  </td>
                  <td>
                    <Link
                      to={`/incidents/${inc.id}`}
                      className="btn btn-secondary btn-sm"
                      style={{ fontSize: '0.75rem', padding: '0.25rem 0.55rem' }}
                    >
                      Details &rarr;
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
