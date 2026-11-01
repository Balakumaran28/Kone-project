import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  fetchIncidentById,
  acknowledgeIncident,
  assignIncident,
  updateIncidentStatus,
  fetchTechnicians,
  addIncidentNote,
  evaluateSystemConnectivity,
  fetchIncidentSla,
} from '../services/api';
import { enqueueAction } from '../services/offlineQueue';

export default function IncidentDetailPage() {
  const { id } = useParams();
  const { user, token } = useAuth();

  const [incidentDetail, setIncidentDetail] = useState(null);
  const [slaDetail, setSlaDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notification, setNotification] = useState('');
  const [isOffline, setIsOffline] = useState(false);

  // Triage state
  const [technicians, setTechnicians] = useState([]);
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [selectedTechId, setSelectedTechId] = useState('');
  const [assignRemarks, setAssignRemarks] = useState('');
  const [assigning, setAssigning] = useState(false);

  const [showResolveModal, setShowResolveModal] = useState(false);
  const [resolutionNotes, setResolutionNotes] = useState('');
  const [resolveElevatorStatus, setResolveElevatorStatus] = useState('ACTIVE');
  const [resolving, setResolving] = useState(false);

  // Note Modal state
  const [showNoteModal, setShowNoteModal] = useState(false);
  const [noteContent, setNoteContent] = useState('');
  const [noteType, setNoteType] = useState('WORK_LOG');
  const [savingNote, setSavingNote] = useState(false);

  // Timeline filtering and sorting
  const [timelineSort, setTimelineSort] = useState('ASC'); // 'ASC' = oldest first, 'DESC' = newest first
  const [timelineFilter, setTimelineFilter] = useState('ALL'); // 'ALL', 'STATUS', 'ASSIGNMENT', 'NOTES', 'ELEVATOR'

  const [actionLoading, setActionLoading] = useState(false);

  // Role permissions
  const isResident = user?.role === 'RESIDENT';
  const canAcknowledge = ['SECURITY_STAFF', 'BUILDING_MANAGER', 'TECHNICIAN', 'COMPANY_SUPERVISOR', 'ADMINISTRATOR'].includes(user?.role);
  const canAssign = ['BUILDING_MANAGER', 'SECURITY_STAFF', 'COMPANY_SUPERVISOR', 'ADMINISTRATOR'].includes(user?.role);
  const canUpdateStatus = ['TECHNICIAN', 'BUILDING_MANAGER', 'ADMINISTRATOR'].includes(user?.role);
  const canClose = ['BUILDING_MANAGER', 'ADMINISTRATOR'].includes(user?.role);
  const canAddNote = ['TECHNICIAN', 'BUILDING_MANAGER', 'SECURITY_STAFF', 'ADMINISTRATOR', 'COMPANY_SUPERVISOR'].includes(user?.role)
    || (isResident && incidentDetail?.incident?.status !== 'CLOSED');

  const checkConnectivity = useCallback(async () => {
    try {
      const conn = await evaluateSystemConnectivity();
      setIsOffline(conn.state === 'BACKEND_UNAVAILABLE' || conn.state === 'DATABASE_UNAVAILABLE');
    } catch {
      setIsOffline(true);
    }
  }, []);

  const loadIncident = useCallback(async () => {
    if (!token || !id) return;
    setLoading(true);
    setError('');
    try {
      const data = await fetchIncidentById(token, id);
      setIncidentDetail(data);

      try {
        const slaData = await fetchIncidentSla(token, id);
        setSlaDetail(slaData);
      } catch {
        // SLA info optional for residents or when unconfigured
      }

      checkConnectivity();
    } catch (err) {
      setError(err.message || 'Unable to retrieve incident details.');
      checkConnectivity();
    } finally {
      setLoading(false);
    }
  }, [token, id, checkConnectivity]);

  useEffect(() => {
    loadIncident();
  }, [loadIncident]);

  const showNotificationMsg = (msg) => {
    setNotification(msg);
    setTimeout(() => setNotification(''), 4500);
  };

  // 1. Acknowledge Action
  const handleAcknowledge = async () => {
    setActionLoading(true);
    try {
      await acknowledgeIncident(token, id, 'Verified and acknowledged by on-duty personnel');
      showNotificationMsg('Incident acknowledged successfully.');
      await loadIncident();
    } catch (err) {
      if (!navigator.onLine || err.message?.includes('NetworkError') || err.message?.includes('Failed to fetch')) {
        await enqueueAction('ACKNOWLEDGE_INCIDENT', { incidentId: Number(id), remarks: 'Acknowledged offline' }, user?.email);
        showNotificationMsg('Buffered offline: Acknowledgment will sync when online.');
      } else {
        setError(err.message || 'Failed to acknowledge incident.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  // 2. Open Assign Modal
  const openAssignModal = async () => {
    setShowAssignModal(true);
    try {
      const list = await fetchTechnicians(token);
      setTechnicians(list);
      if (list.length > 0) {
        setSelectedTechId(String(list[0].id));
      }
    } catch (err) {
      setError(err.message || 'Failed to load technician roster.');
    }
  };

  // 3. Submit Assignment
  const handleAssignSubmit = async (e) => {
    e.preventDefault();
    if (!selectedTechId) return;

    setAssigning(true);
    try {
      await assignIncident(token, id, {
        technicianId: Number(selectedTechId),
        remarks: assignRemarks.trim() || 'Dispatched for physical diagnostic and service inspection',
      });
      setShowAssignModal(false);
      setAssignRemarks('');
      showNotificationMsg('Technician assigned to work order.');
      await loadIncident();
    } catch (err) {
      if (!navigator.onLine || err.message?.includes('NetworkError') || err.message?.includes('Failed to fetch')) {
        await enqueueAction('ASSIGN_INCIDENT', { incidentId: Number(id), technicianId: Number(selectedTechId), remarks: assignRemarks.trim() }, user?.email);
        setShowAssignModal(false);
        showNotificationMsg('Buffered offline: Assignment will sync when online.');
      } else {
        setError(err.message || 'Failed to assign technician.');
      }
    } finally {
      setAssigning(false);
    }
  };

  // 4. Start Work
  const handleStartWork = async () => {
    setActionLoading(true);
    try {
      await updateIncidentStatus(token, id, {
        newStatus: 'IN_PROGRESS',
        remarks: 'Physical shaft inspection and repair procedures initiated on-site',
      });
      showNotificationMsg('Incident updated to IN_PROGRESS.');
      await loadIncident();
    } catch (err) {
      if (!navigator.onLine || err.message?.includes('NetworkError') || err.message?.includes('Failed to fetch')) {
        await enqueueAction('UPDATE_INCIDENT_STATUS', { incidentId: Number(id), newStatus: 'IN_PROGRESS', remarks: 'Work started offline' }, user?.email);
        showNotificationMsg('Buffered offline: Status update will sync when online.');
      } else {
        setError(err.message || 'Failed to update incident status.');
      }
    } finally {
      setActionLoading(false);
    }
  };

  // 5. Submit Resolution
  const handleResolveSubmit = async (e) => {
    e.preventDefault();
    if (!resolutionNotes.trim()) return;

    setResolving(true);
    try {
      await updateIncidentStatus(token, id, {
        newStatus: 'RESOLVED',
        remarks: resolutionNotes.trim(),
        elevatorOperationalStatus: resolveElevatorStatus,
      });
      setShowResolveModal(false);
      setResolutionNotes('');
      showNotificationMsg('Incident marked as RESOLVED.');
      await loadIncident();
    } catch (err) {
      if (!navigator.onLine || err.message?.includes('NetworkError') || err.message?.includes('Failed to fetch')) {
        await enqueueAction('UPDATE_INCIDENT_STATUS', { incidentId: Number(id), newStatus: 'RESOLVED', remarks: resolutionNotes.trim(), elevatorOperationalStatus: resolveElevatorStatus }, user?.email);
        setShowResolveModal(false);
        showNotificationMsg('Buffered offline: Resolution will sync when online.');
      } else {
        setError(err.message || 'Failed to record resolution.');
      }
    } finally {
      setResolving(false);
    }
  };

  // 6. Close Incident
  const handleCloseIncident = async () => {
    if (!window.confirm('Close this incident ticket? All audit logs and technician history will be permanently locked.')) {
      return;
    }
    setActionLoading(true);
    try {
      await updateIncidentStatus(token, id, {
        newStatus: 'CLOSED',
        remarks: 'Verified complete and closed by facility management',
      });
      showNotificationMsg('Incident closed.');
      await loadIncident();
    } catch (err) {
      setError(err.message || 'Failed to close incident ticket.');
    } finally {
      setActionLoading(false);
    }
  };

  // 7. Add Audit Note
  const handleNoteSubmit = async (e) => {
    e.preventDefault();
    if (!noteContent.trim()) return;

    setSavingNote(true);
    try {
      await addIncidentNote(token, id, {
        note: noteContent.trim(),
        noteType,
      });
      setShowNoteModal(false);
      setNoteContent('');
      showNotificationMsg('Audit note recorded to timeline.');
      await loadIncident();
    } catch (err) {
      if (!navigator.onLine || err.message?.includes('NetworkError') || err.message?.includes('Failed to fetch')) {
        await enqueueAction('ADD_INCIDENT_NOTE', { incidentId: Number(id), note: noteContent.trim(), noteType }, user?.email);
        setShowNoteModal(false);
        showNotificationMsg('Buffered offline: Note will sync when online.');
      } else {
        setError(err.message || 'Failed to append note.');
      }
    } finally {
      setSavingNote(false);
    }
  };

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

  const getEventPresentation = (entry) => {
    const action = entry.action || entry.changeType || 'UNKNOWN';
    switch (action) {
      case 'INCIDENT_REPORTED':
      case 'REPORTED':
        return { title: 'Ticket Created & Reported', icon: '📝', color: '#38bdf8', category: 'STATUS' };
      case 'ACKNOWLEDGED':
        return { title: 'Incident Acknowledged', icon: '✓', color: '#60a5fa', category: 'STATUS' };
      case 'ASSIGNED':
      case 'TECHNICIAN_ASSIGNED':
        return { title: 'Technician Assigned', icon: '🔧', color: '#38bdf8', category: 'ASSIGNMENT' };
      case 'STATUS_CHANGED':
        return { title: 'Status Transitioned', icon: '⚡', color: '#fbbf24', category: 'STATUS' };
      case 'RESOLVED':
        return { title: 'Incident Resolved', icon: '✅', color: '#34d399', category: 'STATUS' };
      case 'CLOSED':
        return { title: 'Ticket Closed', icon: '🔒', color: '#94a3b8', category: 'STATUS' };
      case 'ELEVATOR_OUT_OF_SERVICE':
        return { title: 'Elevator Safety Shutdown', icon: '🛑', color: '#f87171', category: 'ELEVATOR' };
      case 'ELEVATOR_STATUS_CHANGED':
        return { title: 'Elevator Operational Status Changed', icon: '🛗', color: '#38bdf8', category: 'ELEVATOR' };
      case 'WORK_NOTE_ADDED':
        return { title: 'Work Log Appended', icon: '📋', color: '#cbd5e1', category: 'NOTES' };
      case 'SLA_WARNING_TRIGGERED':
        return { title: 'SLA Warning Triggered', icon: '⚠️', color: '#fbbf24', category: 'STATUS' };
      case 'RESPONSE_SLA_BREACHED':
      case 'RESOLUTION_SLA_BREACHED':
        return { title: 'SLA Deadline Breached', icon: '⏱️', color: '#f87171', category: 'STATUS' };
      case 'INCIDENT_ESCALATED':
        return { title: 'Automated Tier Escalation', icon: '⚡', color: '#c084fc', category: 'STATUS' };
      default:
        return { title: action.replace(/_/g, ' '), icon: '📌', color: '#94a3b8', category: 'NOTES' };
    }
  };

  const incident = incidentDetail?.incident;
  const history = incidentDetail?.history || [];

  const filteredTimeline = useMemo(() => {
    let list = [...history];
    if (timelineFilter !== 'ALL') {
      list = list.filter((item) => {
        const pres = getEventPresentation(item);
        return pres.category === timelineFilter;
      });
    }
    list.sort((a, b) => {
      const timeA = new Date(a.changedAt).getTime();
      const timeB = new Date(b.changedAt).getTime();
      return timelineSort === 'ASC' ? timeA - timeB : timeB - timeA;
    });
    return list;
  }, [history, timelineFilter, timelineSort]);

  if (loading && !incident) {
    return (
      <div className="card" style={{ textAlign: 'center', padding: '3.5rem' }}>
        <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Loading incident lifecycle telemetry...
        </div>
      </div>
    );
  }

  if (error && !incident) {
    return (
      <div style={{ maxWidth: '800px', margin: '2rem auto' }}>
        <div
          style={{
            padding: '1rem',
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
        <Link to="/incidents" className="btn btn-secondary btn-sm">
          &larr; Back to Incident Queue
        </Link>
      </div>
    );
  }

  return (
    <div>
      {/* Breadcrumb */}
      <div style={{ marginBottom: '1rem' }}>
        <Link to="/incidents" style={{ color: 'var(--accent-cyan)', fontSize: '0.82rem', fontWeight: 600 }}>
          &larr; Back to Incident Queue
        </Link>
      </div>

      {notification && (
        <div
          style={{
            padding: '0.75rem 1rem',
            background: 'var(--success-bg)',
            border: '1px solid var(--success-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--success-text)',
            fontSize: '0.85rem',
            marginBottom: '1.25rem',
          }}
        >
          ✓ {notification}
        </div>
      )}

      {error && (
        <div
          style={{
            padding: '0.75rem 1rem',
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

      {/* Main Incident Card */}
      <div className="card" style={{ marginBottom: '1.5rem', padding: '1.5rem' }}>
        <div className="page-header" style={{ marginBottom: '1rem' }}>
          <div className="page-title-group">
            <h1>
              <span style={{ fontFamily: 'var(--font-mono)' }}>{incident.ticketNumber}</span>
              {getSeverityBadge(incident.severity)}
              {getStatusBadge(incident.status)}
              {incident.entrapmentReported && (
                <span className="badge badge-critical">
                  🚨 PASSENGER ENTRAPMENT
                </span>
              )}
            </h1>
            <p className="page-subtitle">
              Category: <strong>{incident.issueCategoryName}</strong> &bull; Reported {new Date(incident.reportedAt).toLocaleString()}
            </p>
          </div>

          {incident.resolvedAt && (
            <div className="badge badge-success" style={{ padding: '0.35rem 0.65rem' }}>
              ✓ Resolved at {new Date(incident.resolvedAt).toLocaleTimeString()}
            </div>
          )}
        </div>

        {/* Location & Personnel Grid */}
        <div className="metric-grid" style={{ marginBottom: '1.25rem' }}>
          <div className="metric-card">
            <span className="metric-label">Building Complex</span>
            <div className="metric-value" style={{ fontSize: '1.05rem' }}>
              <Link to={`/buildings/${incident.buildingId}`} style={{ color: 'var(--elevator-blue-primary)', fontWeight: 600 }}>
                {incident.buildingName}
              </Link>
            </div>
            <div className="metric-subtext">Code: {incident.buildingCode}</div>
          </div>

          <div className="metric-card">
            <span className="metric-label">Elevator Shaft</span>
            <div className="metric-value" style={{ fontSize: '1.2rem', fontFamily: 'var(--font-mono)', color: 'var(--text-primary)' }}>
              {incident.elevatorCode}
            </div>
            <div className="metric-subtext">Shaft unit #{incident.elevatorId}</div>
          </div>

          <div className="metric-card">
            <span className="metric-label">Reported By</span>
            <div className="metric-value" style={{ fontSize: '1.05rem', color: 'var(--text-primary)' }}>
              {incident.reportedByName}
            </div>
            <div className="metric-subtext">{incident.reportedByEmail}</div>
          </div>

          <div className="metric-card">
            <span className="metric-label">Assigned Technician</span>
            <div className="metric-value" style={{ fontSize: '1.05rem', color: incident.assignedToName ? '#15803d' : 'var(--text-muted)' }}>
              {incident.assignedToName ? `🔧 ${incident.assignedToName}` : 'Unassigned'}
            </div>
            <div className="metric-subtext">{incident.assignedToEmail || 'Awaiting dispatch'}</div>
          </div>
        </div>

        {/* Incident Description */}
        <div style={{ marginBottom: '1.25rem', padding: '1rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: '0.35rem' }}>
            Problem Description & Diagnostics
          </div>
          <div style={{ fontSize: '0.92rem', color: 'var(--text-primary)', lineHeight: 1.5 }}>
            {incident.description}
          </div>
        </div>

        {/* SLA Compliance Section */}
        {slaDetail && (
          <div style={{ padding: '1rem', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem', flexWrap: 'wrap', gap: '0.5rem' }}>
              <strong style={{ fontSize: '0.85rem', color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <span>⏱️</span> Service Level Agreement (SLA) Tracking
              </strong>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Policy: <strong style={{ color: 'var(--elevator-blue-primary)' }}>{slaDetail.slaPolicyName || 'Global Baseline SLA'}</strong>
              </span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.75rem' }}>
              <div style={{ background: 'var(--bg-card)', padding: '0.75rem 1rem', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                <span className="metric-label">Response SLA ({slaDetail.responseTargetMinutes}m target)</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.25rem' }}>
                  <span className={`badge ${slaDetail.responseSlaState === 'BREACHED' ? 'badge-critical' : slaDetail.responseSlaState === 'APPROACHING_DEADLINE' ? 'badge-warning' : 'badge-success'}`}>
                    {slaDetail.responseSlaState || 'ON_TRACK'}
                  </span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    {slaDetail.responseDeadline ? new Date(slaDetail.responseDeadline).toLocaleTimeString() : '—'}
                  </span>
                </div>
              </div>

              <div style={{ background: 'var(--bg-card)', padding: '0.75rem 1rem', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                <span className="metric-label">Resolution SLA ({slaDetail.resolutionTargetMinutes}m target)</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.25rem' }}>
                  <span className={`badge ${slaDetail.resolutionSlaState === 'BREACHED' ? 'badge-critical' : slaDetail.resolutionSlaState === 'APPROACHING_DEADLINE' ? 'badge-warning' : 'badge-success'}`}>
                    {slaDetail.resolutionSlaState || 'ON_TRACK'}
                  </span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                    {slaDetail.resolutionDeadline ? new Date(slaDetail.resolutionDeadline).toLocaleTimeString() : '—'}
                  </span>
                </div>
              </div>

              <div style={{ background: 'var(--bg-card)', padding: '0.75rem 1rem', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
                <span className="metric-label">Escalation Tier</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.25rem' }}>
                  <span className={`badge ${slaDetail.escalationLevel > 0 ? 'badge-warning' : 'badge-neutral'}`}>
                    {slaDetail.escalationLevel > 0 ? `Tier ${slaDetail.escalationLevel}` : 'Normal (Tier 0)'}
                  </span>
                  {slaDetail.lastEscalatedAt && (
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {new Date(slaDetail.lastEscalatedAt).toLocaleTimeString()}
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Staff Triage Operations Action Bar */}
      {!isResident && incident.status !== 'CLOSED' && (
        <div className="card" style={{ marginBottom: '1.5rem', borderLeft: '4px solid var(--elevator-blue-primary)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '0.75rem' }}>
            <h2 style={{ fontSize: '1.05rem', fontWeight: 700, margin: 0, color: 'var(--elevator-blue-dark)' }}>
              Local Triage Operations
            </h2>
            <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              Current: <strong style={{ color: 'var(--text-primary)' }}>{incident.status}</strong>
            </span>
          </div>

          <div style={{ display: 'flex', gap: '0.6rem', flexWrap: 'wrap' }}>
            {canAcknowledge && incident.status === 'REPORTED' && (
              <button
                onClick={handleAcknowledge}
                disabled={actionLoading}
                className="btn btn-primary btn-sm"
              >
                ✓ Acknowledge Ticket
              </button>
            )}

            {canAssign && (
              <button
                onClick={openAssignModal}
                disabled={actionLoading}
                className="btn btn-secondary btn-sm"
              >
                🔧 {incident.assignedToName ? 'Reassign Technician' : 'Assign Technician'}
              </button>
            )}

            {canUpdateStatus && incident.status !== 'IN_PROGRESS' && incident.status !== 'RESOLVED' && (
              <button
                onClick={handleStartWork}
                disabled={actionLoading}
                className="btn btn-warning btn-sm"
              >
                ▶ Start Diagnostic / In Progress
              </button>
            )}

            {canUpdateStatus && incident.status !== 'RESOLVED' && (
              <button
                onClick={() => setShowResolveModal(true)}
                disabled={actionLoading}
                className="btn btn-success btn-sm"
              >
                ✓ Record Resolution
              </button>
            )}

            {canClose && incident.status === 'RESOLVED' && (
              <button
                onClick={handleCloseIncident}
                disabled={actionLoading}
                className="btn btn-ghost btn-sm"
              >
                ✕ Close Ticket
              </button>
            )}

            {canAddNote && (
              <button
                onClick={() => setShowNoteModal(true)}
                className="btn btn-secondary btn-sm"
              >
                📝 Add Work Note
              </button>
            )}
          </div>
        </div>
      )}

      {/* Audit Trail Timeline */}
      <div className="card">
        <div className="card-header">
          <div>
            <h2 className="card-title">Chronological Audit Trail & Triage History ({history.length})</h2>
            <div className="card-subtitle">
              Immutable sequence of ticket events, state transitions, and staff notes
            </div>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}>
            <select
              className="form-select"
              value={timelineFilter}
              onChange={(e) => setTimelineFilter(e.target.value)}
              style={{ fontSize: '0.78rem', padding: '0.35rem 0.6rem', width: 'auto' }}
            >
              <option value="ALL">All Event Types</option>
              <option value="STATUS">Status Transitions</option>
              <option value="ASSIGNMENT">Assignments</option>
              <option value="ELEVATOR">Elevator Status</option>
              <option value="NOTES">Work Notes</option>
            </select>

            <button
              onClick={() => setTimelineSort(timelineSort === 'ASC' ? 'DESC' : 'ASC')}
              className="btn btn-secondary btn-sm"
              style={{ fontSize: '0.78rem' }}
            >
              {timelineSort === 'ASC' ? '⬆ Oldest First' : '⬇ Newest First'}
            </button>

            {isResident && canAddNote && (
              <button
                onClick={() => setShowNoteModal(true)}
                className="btn btn-primary btn-sm"
              >
                📝 Add Note
              </button>
            )}
          </div>
        </div>

        {filteredTimeline.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon">📭</div>
            <h3 className="empty-title">No Audit Events Recorded</h3>
            <p className="empty-desc">
              {timelineFilter !== 'ALL'
                ? `No events match the "${timelineFilter}" filter.`
                : 'No historical changes recorded yet.'}
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {filteredTimeline.map((entry, idx) => {
              const pres = getEventPresentation(entry);
              const isSystem = entry.actorType === 'SYSTEM';

              return (
                <div
                  key={entry.id || idx}
                  style={{
                    display: 'flex',
                    gap: '0.85rem',
                    paddingBottom: idx !== filteredTimeline.length - 1 ? '1rem' : 0,
                    borderBottom: idx !== filteredTimeline.length - 1 ? '1px solid var(--border-subtle)' : 'none',
                  }}
                >
                  <div
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: 'var(--radius-sm)',
                      background: 'var(--bg-secondary)',
                      border: '1px solid var(--border-default)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: '0.9rem',
                      flexShrink: 0,
                    }}
                  >
                    {pres.icon}
                  </div>

                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.2rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <strong style={{ fontSize: '0.88rem', color: 'var(--text-primary)' }}>{pres.title}</strong>
                        {isSystem ? (
                          <span className="badge badge-info" style={{ fontSize: '0.62rem' }}>
                            🤖 SYSTEM AUTOMATION
                          </span>
                        ) : entry.changedByName ? (
                          <span style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>
                            by <strong style={{ color: 'var(--text-primary)' }}>{entry.changedByName}</strong> ({entry.changedByRole})
                          </span>
                        ) : null}
                      </div>

                      <span style={{ fontSize: '0.74rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                        {new Date(entry.changedAt).toLocaleString()}
                      </span>
                    </div>

                    {(entry.previousValue || entry.newValue) && (
                      <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '0.4rem', marginTop: '0.25rem' }}>
                        {entry.previousValue && (
                          <>
                            <span style={{ color: 'var(--text-muted)' }}>Previous:</span>
                            <code>{entry.previousValue}</code>
                            <span>&rarr;</span>
                          </>
                        )}
                        <span style={{ color: 'var(--text-muted)' }}>New:</span>
                        <code style={{ color: 'var(--elevator-blue-primary)', fontWeight: 600 }}>{entry.newValue}</code>
                      </div>
                    )}

                    {entry.remarks && (
                      <div
                        style={{
                          marginTop: '0.4rem',
                          fontSize: '0.82rem',
                          color: 'var(--text-secondary)',
                          background: 'var(--bg-secondary)',
                          padding: '0.5rem 0.75rem',
                          borderRadius: 'var(--radius-sm)',
                          border: '1px solid var(--border-subtle)',
                        }}
                      >
                        "{entry.remarks}"
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Assign Technician Modal */}
      {showAssignModal && (
        <div className="modal-overlay" onClick={() => setShowAssignModal(false)}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">Dispatch Technician</h2>
              <button className="modal-close" onClick={() => setShowAssignModal(false)}>✕</button>
            </div>
            <form onSubmit={handleAssignSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label" htmlFor="techRoster">
                    Assign Certified Technician <span className="required">*</span>
                  </label>
                  <select
                    id="techRoster"
                    required
                    className="form-select"
                    value={selectedTechId}
                    onChange={(e) => setSelectedTechId(e.target.value)}
                  >
                    {technicians.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.fullName} ({t.email})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="assignNotes">
                    Work Instructions / Dispatch Remarks
                  </label>
                  <textarea
                    id="assignNotes"
                    rows={3}
                    className="form-textarea"
                    value={assignRemarks}
                    onChange={(e) => setAssignRemarks(e.target.value)}
                    placeholder="e.g. Test optical sensors and emergency brake linkage..."
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowAssignModal(false)} disabled={assigning}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary btn-sm" disabled={assigning}>
                  {assigning ? 'Dispatching...' : 'Confirm Assignment'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Resolve Incident Modal */}
      {showResolveModal && (
        <div className="modal-overlay" onClick={() => setShowResolveModal(false)}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">Record Incident Resolution</h2>
              <button className="modal-close" onClick={() => setShowResolveModal(false)}>✕</button>
            </div>
            <form onSubmit={handleResolveSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label" htmlFor="resNotes">
                    Work Performed / Diagnostic Findings <span className="required">*</span>
                  </label>
                  <textarea
                    id="resNotes"
                    required
                    rows={4}
                    className="form-textarea"
                    value={resolutionNotes}
                    onChange={(e) => setResolutionNotes(e.target.value)}
                    placeholder="Describe maintenance actions, replaced components, safety cycle tests..."
                  />
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="elvAfter">
                    Elevator Shaft Status After Resolution
                  </label>
                  <select
                    id="elvAfter"
                    className="form-select"
                    value={resolveElevatorStatus}
                    onChange={(e) => setResolveElevatorStatus(e.target.value)}
                  >
                    <option value="ACTIVE">ACTIVE (Return elevator shaft to normal service)</option>
                    <option value="MAINTENANCE">MAINTENANCE (Keep in diagnostic test mode)</option>
                    <option value="OUT_OF_SERVICE">OUT_OF_SERVICE (Remain disabled pending parts)</option>
                  </select>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowResolveModal(false)} disabled={resolving}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-success btn-sm" disabled={resolving}>
                  {resolving ? 'Recording...' : 'Confirm Resolution'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Note Modal */}
      {showNoteModal && (
        <div className="modal-overlay" onClick={() => setShowNoteModal(false)}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">Append Audit Note</h2>
              <button className="modal-close" onClick={() => setShowNoteModal(false)}>✕</button>
            </div>
            <form onSubmit={handleNoteSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label" htmlFor="noteTypeSel">Note Category</label>
                  <select
                    id="noteTypeSel"
                    className="form-select"
                    value={noteType}
                    onChange={(e) => setNoteType(e.target.value)}
                  >
                    <option value="WORK_LOG">Work / Diagnostic Log</option>
                    <option value="RESOLUTION">Resolution / Follow-up Note</option>
                    {!isResident && <option value="ADMINISTRATIVE">Administrative Override Note</option>}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="noteBody">
                    Note Description <span className="required">*</span>
                  </label>
                  <textarea
                    id="noteBody"
                    required
                    rows={4}
                    className="form-textarea"
                    value={noteContent}
                    onChange={(e) => setNoteContent(e.target.value)}
                    placeholder="Enter audit observations, parts ordered, or resident communications..."
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowNoteModal(false)} disabled={savingNote}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary btn-sm" disabled={savingNote}>
                  {savingNote ? 'Appending...' : 'Save Audit Note'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
