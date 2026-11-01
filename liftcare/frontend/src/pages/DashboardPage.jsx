import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  fetchDashboardSummary,
  fetchIncidents,
  fetchAllElevators,
} from '../services/api';
import {
  ElevatorIcon,
  IncidentIcon,
  MaintenanceIcon,
  CheckCircleIcon,
  AlertCircleIcon,
  ClockIcon,
} from '../components/ElevatorIcons';

export default function DashboardPage() {
  const { user, token } = useAuth();
  const [summary, setSummary] = useState(null);
  const [incidents, setIncidents] = useState([]);
  const [elevators, setElevators] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadDashboardData = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    try {
      const [summaryData, incidentList, elevatorList] = await Promise.all([
        fetchDashboardSummary(token).catch(() => null),
        fetchIncidents(token).catch(() => []),
        fetchAllElevators(token).catch(() => []),
      ]);
      setSummary(summaryData);
      setIncidents(incidentList);
      setElevators(elevatorList);
    } catch (err) {
      setError(err.message || 'Unable to load dashboard data');
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  // Derived real metrics
  const totalElevators = summary?.totalElevators ?? elevators.length;
  const operationalElevators = summary?.elevatorsActive ?? elevators.filter((e) => e.operationalStatus === 'ACTIVE').length;
  const underMaintenance = summary?.elevatorsUnderMaintenance ?? elevators.filter((e) => e.operationalStatus === 'MAINTENANCE').length;
  const outOfService = summary?.elevatorsOutOfService ?? elevators.filter((e) => e.operationalStatus === 'OUT_OF_SERVICE').length;
  const totalActiveIncidents = summary?.totalActiveIncidents ?? incidents.filter((i) => i.status !== 'CLOSED' && i.status !== 'RESOLVED').length;
  const criticalIncidents = summary?.criticalIncidents ?? incidents.filter((i) => i.severity === 'CRITICAL' && i.status !== 'CLOSED').length;
  const activeEntrapments = summary?.activeEntrapments ?? incidents.filter((i) => i.entrapmentReported && i.status !== 'CLOSED').length;

  const getElevatorBadge = (status) => {
    switch (status) {
      case 'ACTIVE':
        return <span className="badge badge-operational">Operational</span>;
      case 'MAINTENANCE':
        return <span className="badge badge-maintenance">Maintenance</span>;
      case 'OUT_OF_SERVICE':
        return <span className="badge badge-out-of-service">Out of Service</span>;
      default:
        return <span className="badge badge-low">{status || 'Unknown'}</span>;
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
        return <span className="badge badge-info">ACKNOWLEDGED</span>;
      case 'ASSIGNED':
        return <span className="badge badge-info">ASSIGNED</span>;
      case 'IN_PROGRESS':
        return <span className="badge badge-maintenance">IN PROGRESS</span>;
      case 'RESOLVED':
        return <span className="badge badge-operational">RESOLVED</span>;
      case 'CLOSED':
        return <span className="badge badge-neutral">CLOSED</span>;
      default:
        return <span className="badge badge-neutral">{status}</span>;
    }
  };

  return (
    <div>
      {/* 1. Concise Dashboard Heading */}
      <div className="dashboard-header">
        <div className="dashboard-heading-group">
          <h1>Elevator Operations</h1>
          <p className="dashboard-heading-desc">
            Monitor elevator availability, incidents, and maintenance activity across building shafts.
          </p>
        </div>

        <div className="dashboard-actions">
          <Link to="/incidents/report" className="btn btn-primary btn-sm">
            <span>+</span> Report an Issue
          </Link>
          {user?.role !== 'RESIDENT' && (
            <Link to="/control-room" className="btn btn-secondary btn-sm">
              Control Room Dispatch &rarr;
            </Link>
          )}
          <button onClick={loadDashboardData} className="btn btn-secondary btn-sm" disabled={loading}>
            {loading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {error && (
        <div className="elev-card" style={{ padding: '1rem', borderLeft: '4px solid var(--safety-red)', marginBottom: '1.5rem', background: 'var(--safety-red-bg)' }}>
          <strong style={{ color: 'var(--safety-red)' }}>⚠️ {error}</strong>
        </div>
      )}

      {/* 2. Critical Entrapment Alert Banner (Only when active entrapments > 0) */}
      {activeEntrapments > 0 && (
        <div className="emergency-intake-banner">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <div className="emergency-banner-title">
                <AlertCircleIcon size={20} color="#dc2626" />
                <span>ACTIVE ENTRAPMENT ALERT &bull; {activeEntrapments} INCIDENT(S) REQUIRING RESCUE</span>
              </div>
              <p className="emergency-banner-text">
                Passenger entrapment reported inside building elevator shaft. Immediate emergency staff verification and dispatch protocol active.
              </p>
            </div>
            <Link to="/incidents?entrapment=true" className="btn btn-danger btn-sm">
              View Emergency Queue &rarr;
            </Link>
          </div>
        </div>
      )}

      {/* 3. Compact KPI Cards using Real Backend Data */}
      <div className="kpi-grid">
        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">Total Elevators</span>
            <ElevatorIcon size={18} color="var(--text-muted)" />
          </div>
          <div className="kpi-value">{loading ? '—' : totalElevators}</div>
          <div className="kpi-subtext">Registered elevator shafts</div>
        </div>

        <div className="kpi-card status-operational">
          <div className="kpi-header">
            <span className="kpi-label" style={{ color: 'var(--safety-green)' }}>Operational</span>
            <CheckCircleIcon size={18} color="var(--safety-green)" />
          </div>
          <div className="kpi-value" style={{ color: 'var(--safety-green)' }}>
            {loading ? '—' : operationalElevators}
          </div>
          <div className="kpi-subtext">In normal passenger service</div>
        </div>

        <div className={`kpi-card ${underMaintenance > 0 ? 'alert-warning' : ''}`}>
          <div className="kpi-header">
            <span className="kpi-label" style={{ color: underMaintenance > 0 ? 'var(--safety-amber)' : 'var(--text-muted)' }}>
              Under Maintenance
            </span>
            <MaintenanceIcon size={18} color={underMaintenance > 0 ? 'var(--safety-amber)' : 'var(--text-muted)'} />
          </div>
          <div className="kpi-value" style={{ color: underMaintenance > 0 ? 'var(--safety-amber)' : 'var(--text-main)' }}>
            {loading ? '—' : underMaintenance}
          </div>
          <div className="kpi-subtext">{outOfService > 0 ? `${outOfService} out of service` : 'Routine service & diagnostic'}</div>
        </div>

        <div className="kpi-card">
          <div className="kpi-header">
            <span className="kpi-label">Active Incidents</span>
            <IncidentIcon size={18} color="var(--text-muted)" />
          </div>
          <div className="kpi-value">{loading ? '—' : totalActiveIncidents}</div>
          <div className="kpi-subtext">Open lifecycle tickets</div>
        </div>

        <div className={`kpi-card ${criticalIncidents > 0 ? 'alert-critical' : ''}`}>
          <div className="kpi-header">
            <span className="kpi-label" style={{ color: criticalIncidents > 0 ? 'var(--safety-red)' : 'var(--text-muted)' }}>
              Critical Incidents
            </span>
            <AlertCircleIcon size={18} color={criticalIncidents > 0 ? 'var(--safety-red)' : 'var(--text-muted)'} />
          </div>
          <div className="kpi-value" style={{ color: criticalIncidents > 0 ? 'var(--safety-red)' : 'var(--text-main)' }}>
            {loading ? '—' : criticalIncidents}
          </div>
          <div className="kpi-subtext">Priority emergency dispatch</div>
        </div>
      </div>

      {/* 4. Elevator Status Overview & Recent Activity */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(480px, 1fr))', gap: '1.5rem', marginBottom: '1.75rem' }}>
        {/* Elevator Status Overview */}
        <div className="elev-card" style={{ margin: 0 }}>
          <div className="elev-card-header">
            <div>
              <h2 className="elev-card-title">
                <ElevatorIcon size={18} color="var(--elevator-blue)" />
                <span>Elevator Fleet Status</span>
              </h2>
              <div className="elev-card-subtitle">
                Current recorded operational states across monitored shafts
              </div>
            </div>
            <Link to="/buildings" className="btn btn-secondary btn-sm">
              Manage Fleet &rarr;
            </Link>
          </div>

          <div className="table-responsive">
            {elevators.length === 0 ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                {loading ? 'Querying local elevator registry...' : 'No elevators currently registered.'}
              </div>
            ) : (
              <table className="elev-table">
                <thead>
                  <tr>
                    <th>Elevator Code</th>
                    <th>Location / Shaft</th>
                    <th>Floors</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {elevators.slice(0, 6).map((elv) => (
                    <tr key={elv.id}>
                      <td>
                        <strong style={{ color: 'var(--elevator-blue-dark)' }}>{elv.elevatorCode}</strong>
                      </td>
                      <td>
                        <span style={{ fontSize: '0.82rem' }}>
                          {elv.locationDescription || 'Main Shaft'}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.82rem' }}>
                        {elv.totalFloors ? `${elv.totalFloors} fl.` : '—'}
                      </td>
                      <td>{getElevatorBadge(elv.operationalStatus)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div style={{ padding: '0.75rem 1.25rem', background: 'var(--bg-surface-alt)', borderTop: '1px solid var(--border-light)', fontSize: '0.74rem', color: 'var(--text-muted)' }}>
            Note: Status records reflect software operational states on this local building server and do not represent direct sensor hardware streams.
          </div>
        </div>

        {/* Recent Incidents Overview */}
        <div className="elev-card" style={{ margin: 0 }}>
          <div className="elev-card-header">
            <div>
              <h2 className="elev-card-title">
                <IncidentIcon size={18} color="var(--safety-amber)" />
                <span>Recent Incident Activity</span>
              </h2>
              <div className="elev-card-subtitle">
                Open tickets and maintenance reports
              </div>
            </div>
            <Link to="/incidents" className="btn btn-secondary btn-sm">
              View All ({incidents.length})
            </Link>
          </div>

          <div className="table-responsive">
            {incidents.length === 0 ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                {loading ? 'Retrieving local incident reports...' : 'All elevators running with zero open incidents.'}
              </div>
            ) : (
              <table className="elev-table">
                <thead>
                  <tr>
                    <th>Ticket</th>
                    <th>Elevator</th>
                    <th>Severity</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {incidents.slice(0, 6).map((inc) => (
                    <tr key={inc.id}>
                      <td>
                        <Link to={`/incidents/${inc.id}`} className="ticket-number-tag">
                          {inc.ticketNumber}
                        </Link>
                        {inc.entrapmentReported && (
                          <span className="badge badge-critical" style={{ marginLeft: '0.35rem', fontSize: '0.62rem' }}>
                            ENTRAPMENT
                          </span>
                        )}
                      </td>
                      <td>
                        <code>{inc.elevatorCode || 'Shaft #' + inc.elevatorId}</code>
                      </td>
                      <td>{getSeverityBadge(inc.severity)}</td>
                      <td>{getStatusBadge(inc.status)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div style={{ padding: '0.75rem 1.25rem', background: 'var(--bg-surface-alt)', borderTop: '1px solid var(--border-light)', display: 'flex', justifyContent: 'space-between', fontSize: '0.74rem', color: 'var(--text-muted)' }}>
            <span>SLA Monitoring: <strong>Active</strong></span>
            <span>Overdue Escalations: <strong>{summary?.resolutionSlaBreachedCount ?? 0}</strong></span>
          </div>
        </div>
      </div>

      {/* 5. Local Server & SLA Escalation Monitoring Card */}
      <div className="elev-card">
        <div className="elev-card-header">
          <div>
            <h2 className="elev-card-title">
              <ClockIcon size={18} color="var(--elevator-blue)" />
              <span>Local Server & SLA Automatic Escalation</span>
            </h2>
            <div className="elev-card-subtitle">
              SLA deadline enforcement and tiered escalation engine on local building server
            </div>
          </div>
          {user?.role !== 'RESIDENT' && (
            <Link to="/cloud-sync" className="btn btn-secondary btn-sm">
              Cloud Sync Telemetry &rarr;
            </Link>
          )}
        </div>

        <div className="elev-card-body">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
            <div style={{ padding: '1rem', background: 'var(--bg-surface-alt)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-light)' }}>
              <div style={{ fontSize: '0.74rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                Response Deadlines
              </div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, color: summary?.responseSlaBreachedCount > 0 ? 'var(--safety-red)' : 'var(--safety-green)', marginTop: '0.25rem' }}>
                {summary?.responseSlaBreachedCount > 0 ? `${summary.responseSlaBreachedCount} Breached` : 'On Track'}
              </div>
              <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                Initial triage acknowledgment target
              </div>
            </div>

            <div style={{ padding: '1rem', background: 'var(--bg-surface-alt)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-light)' }}>
              <div style={{ fontSize: '0.74rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                Resolution Deadlines
              </div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, color: summary?.resolutionSlaBreachedCount > 0 ? 'var(--safety-red)' : 'var(--safety-green)', marginTop: '0.25rem' }}>
                {summary?.resolutionSlaBreachedCount > 0 ? `${summary.resolutionSlaBreachedCount} Overdue` : 'On Track'}
              </div>
              <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                Work order restoration target
              </div>
            </div>

            <div style={{ padding: '1rem', background: 'var(--bg-surface-alt)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-light)' }}>
              <div style={{ fontSize: '0.74rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                Automatic Escalations
              </div>
              <div style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--elevator-blue-dark)', marginTop: '0.25rem' }}>
                {(summary?.escalatedTier1Count ?? 0) + (summary?.escalatedTier2Count ?? 0) + (summary?.escalatedTier3Count ?? 0)} Tier Events
              </div>
              <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                Tier 1 (Tech) &bull; Tier 2 (Manager) &bull; Tier 3 (Supervisor)
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
