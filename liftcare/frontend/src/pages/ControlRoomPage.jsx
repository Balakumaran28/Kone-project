import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  fetchDashboardSummary,
  fetchIncidents,
  fetchAllElevators,
  fetchBuildings,
  fetchTechnicians,
  updateElevatorStatus,
  checkBackendHealth,
} from '../services/api';

export default function ControlRoomPage() {
  const { user, token } = useAuth();
  const navigate = useNavigate();

  // Selected Building Filter for entire Control Room
  const [selectedBuildingId, setSelectedBuildingId] = useState('');
  const [buildings, setBuildings] = useState([]);
  const [technicians, setTechnicians] = useState([]);

  // Telemetry Summary Data
  const [summary, setSummary] = useState(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState(null);
  const [lastRefreshedAt, setLastRefreshedAt] = useState(null);

  // Active View Tab: 'incidents' | 'fleet'
  const [activeTab, setActiveTab] = useState('incidents');

  // Incident Queue State
  const [incidents, setIncidents] = useState([]);
  const [incidentsLoading, setIncidentsLoading] = useState(true);
  const [incidentsError, setIncidentsError] = useState(null);
  const [severityFilter, setSeverityFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [entrapmentFilter, setEntrapmentFilter] = useState(false);
  const [technicianFilter, setTechnicianFilter] = useState('');
  const [slaStateFilter, setSlaStateFilter] = useState('');
  const [escalationFilter, setEscalationFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [sortOption, setSortOption] = useState('EMERGENCY_FIRST');
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 10;

  // Fleet & Elevator State
  const [elevators, setElevators] = useState([]);
  const [elevatorsLoading, setElevatorsLoading] = useState(false);
  const [elevatorsError, setElevatorsError] = useState(null);
  const [elevatorStatusFilter, setElevatorStatusFilter] = useState('');

  // Status Change Modal State (for Admin, Manager, Technician)
  const [selectedElevatorForStatus, setSelectedElevatorForStatus] = useState(null);
  const [newOperationalStatus, setNewOperationalStatus] = useState('');
  const [statusModalLoading, setStatusModalLoading] = useState(false);
  const [statusModalError, setStatusModalError] = useState(null);

  // Offline / Connectivity State
  const [isBrowserOnline, setIsBrowserOnline] = useState(navigator.onLine);
  const [backendLatency, setBackendLatency] = useState(null);
  const [backendReachable, setBackendReachable] = useState(true);
  const [isStaleData, setIsStaleData] = useState(false);

  // Auto-refresh configuration (30s)
  const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(true);
  const refreshTimerRef = useRef(null);

  // Staff Authorization Check: RESIDENT is not permitted
  const isResident = user?.role === 'RESIDENT';
  const canModifyElevatorStatus = ['ADMINISTRATOR', 'BUILDING_MANAGER', 'TECHNICIAN'].includes(user?.role);

  // Connectivity event listeners
  useEffect(() => {
    const handleOnline = () => setIsBrowserOnline(true);
    const handleOffline = () => setIsBrowserOnline(false);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // Format elapsed time (e.g., "15m ago", "2h 30m ago")
  const formatTimeElapsed = (timestamp) => {
    if (!timestamp) return '—';
    try {
      const now = new Date();
      const reported = new Date(timestamp);
      const diffMs = now - reported;
      if (diffMs < 0) return 'Just now';

      const diffSec = Math.floor(diffMs / 1000);
      const diffMin = Math.floor(diffSec / 60);
      const diffHours = Math.floor(diffMin / 60);
      const diffDays = Math.floor(diffHours / 24);

      if (diffMin < 1) return `${diffSec}s ago`;
      if (diffMin < 60) return `${diffMin}m ago`;
      if (diffHours < 24) return `${diffHours}h ${diffMin % 60}m ago`;
      return `${diffDays}d ${diffHours % 24}h ago`;
    } catch {
      return timestamp;
    }
  };

  // Load auxiliary reference data (Buildings, Technicians)
  useEffect(() => {
    if (!token || isResident) return;

    let mounted = true;
    const loadAuxData = async () => {
      try {
        const [bldList, techList] = await Promise.all([
          fetchBuildings(token, false).catch(() => []),
          fetchTechnicians(token).catch(() => []),
        ]);
        if (mounted) {
          setBuildings(bldList);
          setTechnicians(techList);
        }
      } catch (err) {
        console.error('Failed to load auxiliary data:', err);
      }
    };

    loadAuxData();
    return () => {
      mounted = false;
    };
  }, [token, isResident]);

  // Primary data load function: fetches summary and queue
  const loadControlRoomData = useCallback(async () => {
    if (!token || isResident) return;

    // Check health probe in parallel
    checkBackendHealth(3000).then((health) => {
      setBackendReachable(health.reachable);
      setBackendLatency(health.latency);
      if (!health.reachable) {
        setIsStaleData(true);
      } else {
        setIsStaleData(false);
      }
    });

    // 1. Fetch Summary
    setSummaryLoading(true);
    setSummaryError(null);
    try {
      const summaryData = await fetchDashboardSummary(token, selectedBuildingId || null);
      setSummary(summaryData);
      setLastRefreshedAt(new Date());
      setIsStaleData(false);
    } catch (err) {
      setSummaryError(err.message || 'Unable to load control-room telemetry summary.');
      setIsStaleData(true);
    } finally {
      setSummaryLoading(false);
    }

    // 2. Fetch Incidents Queue
    setIncidentsLoading(true);
    setIncidentsError(null);
    try {
      const filters = {};
      if (selectedBuildingId) filters.buildingId = selectedBuildingId;
      if (severityFilter) filters.severity = severityFilter;
      if (statusFilter) filters.status = statusFilter;
      if (entrapmentFilter) filters.entrapmentOnly = true;
      if (technicianFilter) filters.assignedToId = technicianFilter;

      const incidentData = await fetchIncidents(token, filters);
      setIncidents(incidentData);
    } catch (err) {
      setIncidentsError(err.message || 'Unable to load incidents for control room queue.');
    } finally {
      setIncidentsLoading(false);
    }

    // 3. Fetch Elevators for Fleet view
    setElevatorsLoading(true);
    setElevatorsError(null);
    try {
      const elevatorData = await fetchAllElevators(token, {
        buildingId: selectedBuildingId || null,
        status: elevatorStatusFilter || null,
      });
      setElevators(elevatorData);
    } catch (err) {
      setElevatorsError(err.message || 'Unable to load elevator fleet status.');
    } finally {
      setElevatorsLoading(false);
    }
  }, [
    token,
    isResident,
    selectedBuildingId,
    severityFilter,
    statusFilter,
    entrapmentFilter,
    technicianFilter,
    elevatorStatusFilter,
  ]);

  // Initial load when filters change
  useEffect(() => {
    loadControlRoomData();
  }, [loadControlRoomData]);

  // Periodic Auto-refresh (30s) with Visibility Awareness
  useEffect(() => {
    if (!autoRefreshEnabled || isResident) {
      if (refreshTimerRef.current) clearInterval(refreshTimerRef.current);
      return;
    }

    const intervalMs = 30000;
    refreshTimerRef.current = setInterval(() => {
      if (!document.hidden) {
        loadControlRoomData();
      }
    }, intervalMs);

    const handleVisibilityChange = () => {
      if (!document.hidden && autoRefreshEnabled) {
        loadControlRoomData();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      if (refreshTimerRef.current) clearInterval(refreshTimerRef.current);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [autoRefreshEnabled, isResident, loadControlRoomData]);

  // Sorting and Filtering logic for Incident Queue
  const processedIncidents = React.useMemo(() => {
    let list = [...incidents];

    // Client-side text search
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      list = list.filter(
        (i) =>
          (i.ticketNumber && i.ticketNumber.toLowerCase().includes(q)) ||
          (i.elevatorCode && i.elevatorCode.toLowerCase().includes(q)) ||
          (i.buildingName && i.buildingName.toLowerCase().includes(q)) ||
          (i.description && i.description.toLowerCase().includes(q)) ||
          (i.issueCategoryName && i.issueCategoryName.toLowerCase().includes(q)) ||
          (i.assignedToName && i.assignedToName.toLowerCase().includes(q))
      );
    }

    // SLA State Filter
    if (slaStateFilter) {
      list = list.filter((i) => {
        if (slaStateFilter === 'BREACHED') {
          return i.responseSlaBreached || i.resolutionSlaBreached || i.responseSlaState === 'BREACHED' || i.resolutionSlaState === 'BREACHED';
        }
        if (slaStateFilter === 'APPROACHING_DEADLINE') {
          return i.responseSlaState === 'APPROACHING_DEADLINE' || i.resolutionSlaState === 'APPROACHING_DEADLINE';
        }
        if (slaStateFilter === 'ON_TRACK') {
          return i.responseSlaState === 'ON_TRACK' || i.resolutionSlaState === 'ON_TRACK';
        }
        if (slaStateFilter === 'MET') {
          return i.responseSlaState === 'MET' && i.resolutionSlaState === 'MET';
        }
        return true;
      });
    }

    // Escalation Level Filter
    if (escalationFilter) {
      const level = parseInt(escalationFilter, 10);
      list = list.filter((i) => (i.escalationLevel ?? 0) === level);
    }

    // Explicit Sort Ordering
    const severityWeight = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

    list.sort((a, b) => {
      if (sortOption === 'EMERGENCY_FIRST') {
        // 1. Entrapments first
        if (a.entrapmentReported && !b.entrapmentReported) return -1;
        if (!a.entrapmentReported && b.entrapmentReported) return 1;

        // 2. Severity weight desc
        const weightA = severityWeight[a.severity] || 0;
        const weightB = severityWeight[b.severity] || 0;
        if (weightA !== weightB) return weightB - weightA;

        // 3. Oldest reportedAt first (urgency)
        return new Date(a.reportedAt) - new Date(b.reportedAt);
      }

      if (sortOption === 'OLDEST_UNACKNOWLEDGED') {
        const isUnackA = a.status === 'REPORTED';
        const isUnackB = b.status === 'REPORTED';
        if (isUnackA && !isUnackB) return -1;
        if (!isUnackA && isUnackB) return 1;
        return new Date(a.reportedAt) - new Date(b.reportedAt);
      }

      if (sortOption === 'NEWEST_FIRST') {
        return new Date(b.reportedAt) - new Date(a.reportedAt);
      }

      if (sortOption === 'OLDEST_FIRST') {
        return new Date(a.reportedAt) - new Date(b.reportedAt);
      }

      return 0;
    });

    return list;
  }, [incidents, searchQuery, sortOption, slaStateFilter, escalationFilter]);

  // Pagination calculation
  const totalPages = Math.ceil(processedIncidents.length / pageSize) || 1;
  const paginatedIncidents = React.useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return processedIncidents.slice(start, start + pageSize);
  }, [processedIncidents, currentPage, pageSize]);

  // Handle Quick Status Update for Elevator
  const handleOpenStatusModal = (elevator) => {
    setSelectedElevatorForStatus(elevator);
    setNewOperationalStatus(elevator.operationalStatus);
    setStatusModalError(null);
  };

  const handleSaveElevatorStatus = async (e) => {
    e.preventDefault();
    if (!selectedElevatorForStatus || !newOperationalStatus) return;

    setStatusModalLoading(true);
    setStatusModalError(null);
    try {
      await updateElevatorStatus(token, selectedElevatorForStatus.id, newOperationalStatus);
      setSelectedElevatorForStatus(null);
      // Reload control room telemetry
      loadControlRoomData();
    } catch (err) {
      setStatusModalError(err.message || 'Failed to update elevator status.');
    } finally {
      setStatusModalLoading(false);
    }
  };

  // Severity styling helper
  const getSeverityBadgeClass = (severity) => {
    switch (severity) {
      case 'CRITICAL':
        return 'severity-badge-critical';
      case 'HIGH':
        return 'severity-badge-high';
      case 'MEDIUM':
        return 'severity-badge-medium';
      case 'LOW':
        return 'severity-badge-low';
      default:
        return 'badge-neutral';
    }
  };

  // Status styling helper
  const getStatusBadgeClass = (status) => {
    switch (status) {
      case 'REPORTED':
        return 'status-badge-reported';
      case 'ACKNOWLEDGED':
        return 'status-badge-acknowledged';
      case 'ASSIGNED':
        return 'status-badge-assigned';
      case 'IN_PROGRESS':
        return 'status-badge-progress';
      case 'RESOLVED':
        return 'status-badge-resolved';
      case 'CLOSED':
        return 'status-badge-closed';
      default:
        return 'badge-neutral';
    }
  };

  // Elevator Operational status badge
  const getElevatorStatusBadge = (status) => {
    switch (status) {
      case 'ACTIVE':
        return { label: 'Active in Service', color: '#10b981', bg: 'rgba(16, 185, 129, 0.15)', border: 'rgba(16, 185, 129, 0.4)' };
      case 'MAINTENANCE':
        return { label: 'Under Maintenance', color: '#f59e0b', bg: 'rgba(245, 158, 11, 0.15)', border: 'rgba(245, 158, 11, 0.4)' };
      case 'OUT_OF_SERVICE':
        return { label: 'Out of Service', color: '#ef4444', bg: 'rgba(239, 68, 68, 0.15)', border: 'rgba(239, 68, 68, 0.4)' };
      default:
        return { label: status, color: '#94a3b8', bg: 'rgba(148, 163, 184, 0.1)', border: 'rgba(148, 163, 184, 0.3)' };
    }
  };

  // Authorization Guard: Forbidden for Residents
  if (isResident) {
    return (
      <div className="container" style={{ padding: '3rem 1.5rem', maxWidth: '800px', margin: '0 auto' }}>
        <div
          style={{
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '12px',
            padding: '2.5rem',
            textAlign: 'center',
          }}
        >
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🚫</div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--accent-rose)', marginBottom: '0.75rem' }}>
            Access Denied: Control Room Restricted
          </h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '1.5rem', lineHeight: 1.6 }}>
            The Building Control Room is an operational dispatch console reserved for Security Staff,
            Facility Managers, Technicians, Supervisors, and Administrators.
          </p>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '2rem' }}>
            As a <strong>RESIDENT</strong>, you may monitor your reported tickets in the standard resident view.
          </p>
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
            <Link to="/incidents" className="btn-primary" style={{ padding: '0.7rem 1.4rem' }}>
              View My Reported Incidents
            </Link>
            <Link to="/dashboard" className="btn-secondary" style={{ padding: '0.7rem 1.4rem' }}>
              Return to Resident Dashboard
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="container" style={{ padding: '2rem 1.5rem', maxWidth: '1400px', margin: '0 auto' }}>
      {/* 1. Header & Live Node Bar */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '1rem',
          paddingBottom: '1.5rem',
          borderBottom: '1px solid var(--border-subtle)',
          marginBottom: '1.5rem',
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.3rem' }}>
            <span style={{ fontSize: '1.6rem' }}>🖥️</span>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 800, margin: 0, letterSpacing: '-0.02em' }}>
              Building Control Room
            </h1>
            <span
              style={{
                fontSize: '0.72rem',
                fontWeight: 700,
                padding: '0.2rem 0.55rem',
                borderRadius: '6px',
                background: 'rgba(56, 189, 248, 0.12)',
                color: 'var(--accent-cyan)',
                border: '1px solid rgba(56, 189, 248, 0.3)',
                textTransform: 'uppercase',
              }}
            >
              Staff Dispatch Console
            </span>
          </div>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', margin: 0 }}>
            Operational Triage, Fleet Safety & Real-Time Emergency Telemetry &bull; Node:{' '}
            <code style={{ color: 'var(--accent-cyan)' }}>{summary?.localNodeId || 'building-local-node-01'}</code>
          </p>
        </div>

        {/* Global Filter & Actions */}
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: '0.75rem' }}>
          {/* Building Scope Selector */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
            <label style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Building:</label>
            <select
              value={selectedBuildingId}
              onChange={(e) => {
                setSelectedBuildingId(e.target.value);
                setCurrentPage(1);
              }}
              style={{
                background: 'var(--bg-card)',
                color: 'var(--text-primary)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '8px',
                padding: '0.45rem 0.75rem',
                fontSize: '0.82rem',
              }}
            >
              <option value="">All Buildings (Local Node)</option>
              {buildings.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name} ({b.buildingCode})
                </option>
              ))}
            </select>
          </div>

          {/* Auto-refresh Switch */}
          <label
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              fontSize: '0.8rem',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              background: 'var(--bg-card)',
              padding: '0.4rem 0.7rem',
              borderRadius: '8px',
              border: '1px solid var(--border-subtle)',
            }}
          >
            <input
              type="checkbox"
              checked={autoRefreshEnabled}
              onChange={(e) => setAutoRefreshEnabled(e.target.checked)}
            />
            Auto-refresh (30s)
          </label>

          {/* Manual Refresh Button */}
          <button
            onClick={loadControlRoomData}
            disabled={summaryLoading || incidentsLoading}
            className="refresh-button"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.45rem 0.85rem',
              fontSize: '0.82rem',
            }}
          >
            <svg
              width="13"
              height="13"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{ animation: summaryLoading || incidentsLoading ? 'spin 1s linear infinite' : 'none' }}
            >
              <polyline points="23 4 23 10 17 10" />
              <polyline points="1 20 1 14 7 14" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            Refresh Now
          </button>
        </div>
      </div>

      {/* 2. Offline / Connectivity Status Banner (Part E) */}
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '0.75rem',
          padding: '0.75rem 1.25rem',
          borderRadius: '10px',
          marginBottom: '1.5rem',
          fontSize: '0.83rem',
          background: !backendReachable
            ? 'rgba(239, 68, 68, 0.15)'
            : !isBrowserOnline
            ? 'rgba(245, 158, 11, 0.15)'
            : 'rgba(16, 185, 129, 0.1)',
          border: `1px solid ${
            !backendReachable
              ? 'rgba(239, 68, 68, 0.4)'
              : !isBrowserOnline
              ? 'rgba(245, 158, 11, 0.4)'
              : 'rgba(16, 185, 129, 0.3)'
          }`,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
          <span
            style={{
              display: 'inline-block',
              width: '10px',
              height: '10px',
              borderRadius: '50%',
              backgroundColor: !backendReachable ? '#ef4444' : '#10b981',
              boxShadow: backendReachable ? '0 0 8px rgba(16, 185, 129, 0.8)' : '0 0 8px rgba(239, 68, 68, 0.8)',
            }}
          />
          <div>
            <strong>Local Server:</strong>{' '}
            {backendReachable ? (
              <span style={{ color: 'var(--accent-emerald)' }}>
                CONNECTED &bull; Latency: {backendLatency ? `${backendLatency}ms` : 'fast'}
              </span>
            ) : (
              <span style={{ color: 'var(--accent-rose)', fontWeight: 700 }}>
                UNREACHABLE &bull; Control room operating on cached state
              </span>
            )}
            {' '}&bull;{' '}
            <strong>Internet:</strong>{' '}
            {isBrowserOnline ? (
              <span style={{ color: 'var(--text-secondary)' }}>Online</span>
            ) : (
              <span style={{ color: 'var(--accent-amber)', fontWeight: 700 }}>
                Offline (Operating normally over building LAN)
              </span>
            )}
          </div>
        </div>

        <div style={{ color: 'var(--text-secondary)', fontSize: '0.78rem' }}>
          {isStaleData && (
            <span style={{ color: 'var(--accent-amber)', marginRight: '0.75rem', fontWeight: 600 }}>
              ⚠️ Stale telemetry notice
            </span>
          )}
          Last updated: {lastRefreshedAt ? lastRefreshedAt.toLocaleTimeString() : 'Loading...'}
        </div>
      </div>

      {/* 3. Prominent Emergency & Entrapment Section (Part A) */}
      <div style={{ marginBottom: '1.75rem' }}>
        {summary?.activeEntrapments > 0 ? (
          <div
            style={{
              background: 'linear-gradient(135deg, rgba(239, 68, 68, 0.22) 0%, rgba(153, 27, 27, 0.28) 100%)',
              border: '2px solid rgba(239, 68, 68, 0.6)',
              borderRadius: '12px',
              padding: '1.25rem 1.5rem',
              boxShadow: '0 0 20px rgba(239, 68, 68, 0.25)',
              animation: 'pulse 2.5s infinite',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '0.75rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <span style={{ fontSize: '1.75rem' }}>🚨</span>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#fca5a5' }}>
                    EMERGENCY ALERT: {summary.activeEntrapments} ACTIVE PASSENGER ENTRAPMENT(S)
                  </h3>
                  <p style={{ margin: 0, fontSize: '0.84rem', color: '#fecaca' }}>
                    Immediate staff dispatch and emergency passenger response protocol required.
                  </p>
                </div>
              </div>
              <button
                onClick={() => {
                  setEntrapmentFilter(true);
                  setActiveTab('incidents');
                }}
                className="btn-danger"
                style={{ padding: '0.5rem 1rem', fontSize: '0.82rem', fontWeight: 700 }}
              >
                Filter Emergency Queue
              </button>
            </div>

            {/* List of Emergency Incident Cards */}
            {summary.emergencyIncidents && summary.emergencyIncidents.length > 0 && (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '0.75rem', marginTop: '1rem' }}>
                {summary.emergencyIncidents.map((inc) => (
                  <div
                    key={inc.id}
                    style={{
                      background: 'rgba(15, 23, 42, 0.75)',
                      border: '1px solid rgba(239, 68, 68, 0.4)',
                      borderRadius: '8px',
                      padding: '0.85rem 1rem',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.4rem',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontFamily: 'monospace', fontWeight: 700, color: '#fca5a5' }}>
                        {inc.ticketNumber}
                      </span>
                      <span className={`status-badge ${getStatusBadgeClass(inc.status)}`} style={{ fontSize: '0.7rem' }}>
                        {inc.status}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.84rem', fontWeight: 600 }}>
                      🏢 {inc.buildingName} &bull; 🛗 {inc.elevatorCode}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                      Category: {inc.issueCategoryName}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: '#fecaca', marginTop: '0.2rem' }}>
                      &bull; Reported {formatTimeElapsed(inc.reportedAt)}
                    </div>
                    <Link
                      to={`/incidents/${inc.id}`}
                      className="btn-secondary"
                      style={{
                        marginTop: '0.5rem',
                        textAlign: 'center',
                        fontSize: '0.78rem',
                        padding: '0.4rem',
                        borderColor: 'rgba(239, 68, 68, 0.5)',
                        color: '#fca5a5',
                      }}
                    >
                      Open Immediate Triage &rarr;
                    </Link>
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div
            style={{
              background: 'rgba(16, 185, 129, 0.08)',
              border: '1px solid rgba(16, 185, 129, 0.25)',
              borderRadius: '10px',
              padding: '0.9rem 1.25rem',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '1rem',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
              <span style={{ fontSize: '1.25rem' }}>✅</span>
              <div>
                <strong style={{ color: 'var(--accent-emerald)', fontSize: '0.92rem' }}>
                  No Active Entrapments Reported
                </strong>
                <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                  All registered elevator cabins on this node are currently free of reported entrapment emergencies.
                </p>
              </div>
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--accent-emerald)', fontWeight: 600 }}>
              Fleet Normal
            </div>
          </div>
        )}
      </div>

      {/* 4. Operational Summary Metric Cards (Part A) */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: '1rem',
          marginBottom: '2rem',
        }}
      >
        {/* Total Active Incidents */}
        <div
          onClick={() => {
            setStatusFilter('');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
            transition: 'transform 0.15s, border-color 0.15s',
          }}
          className="dashboard-card-hover"
        >
          <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600, textTransform: 'uppercase' }}>
            Active Incidents
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--text-primary)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.totalActiveIncidents ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Open lifecycle tickets
          </div>
        </div>

        {/* Critical Incidents */}
        <div
          onClick={() => {
            setSeverityFilter('CRITICAL');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#f87171', fontWeight: 600, textTransform: 'uppercase' }}>
            Critical
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-rose)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.criticalIncidents ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Requires priority dispatch
          </div>
        </div>

        {/* High Severity */}
        <div
          onClick={() => {
            setSeverityFilter('HIGH');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(245, 158, 11, 0.3)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#fbbf24', fontWeight: 600, textTransform: 'uppercase' }}>
            High Severity
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-amber)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.highSeverityIncidents ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Urgent maintenance needs
          </div>
        </div>

        {/* Awaiting Ack */}
        <div
          onClick={() => {
            setStatusFilter('REPORTED');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(14, 165, 233, 0.3)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: 'var(--elevator-blue-primary)', fontWeight: 600, textTransform: 'uppercase' }}>
            Awaiting Ack
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--elevator-blue-primary)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.awaitingAcknowledgementIncidents ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Status: REPORTED
          </div>
        </div>

        {/* Assigned */}
        <div
          onClick={() => {
            setStatusFilter('ASSIGNED');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600, textTransform: 'uppercase' }}>
            Assigned to Tech
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--text-primary)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.assignedIncidents ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Awaiting technician start
          </div>
        </div>

        {/* In Progress */}
        <div
          onClick={() => {
            setStatusFilter('IN_PROGRESS');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border-subtle)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600, textTransform: 'uppercase' }}>
            In Progress
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#a855f7', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.inProgressIncidents ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Under active repair
          </div>
        </div>

        {/* Out of Service Elevators */}
        <div
          onClick={() => {
            setElevatorStatusFilter('OUT_OF_SERVICE');
            setActiveTab('fleet');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#f87171', fontWeight: 600, textTransform: 'uppercase' }}>
            Lifts Out of Service
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-rose)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.elevatorsOutOfService ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Safety or defect shutdown
          </div>
        </div>

        {/* Elevators Under Maintenance */}
        <div
          onClick={() => {
            setElevatorStatusFilter('MAINTENANCE');
            setActiveTab('fleet');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(245, 158, 11, 0.4)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#fbbf24', fontWeight: 600, textTransform: 'uppercase' }}>
            Lifts In Maintenance
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-amber)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.elevatorsUnderMaintenance ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Servicing in progress
          </div>
        </div>
      </div>

      {/* SLA Compliance & Automatic Escalation Telemetry (Module 10) */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: '1rem',
          marginBottom: '2rem',
        }}
      >
        {/* Approaching SLA Deadlines */}
        <div
          onClick={() => {
            setSlaStateFilter('APPROACHING_DEADLINE');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(245, 158, 11, 0.4)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#fbbf24', fontWeight: 600, textTransform: 'uppercase' }}>
            ⚠️ Approaching SLA
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-amber)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.approachingDeadlinesCount ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Within warning window
          </div>
        </div>

        {/* Response SLA Breached */}
        <div
          onClick={() => {
            setSlaStateFilter('BREACHED');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#f87171', fontWeight: 600, textTransform: 'uppercase' }}>
            ⏱️ Response Breached
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-rose)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.responseSlaBreachedCount ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Missed ack target
          </div>
        </div>

        {/* Resolution SLA Breached */}
        <div
          onClick={() => {
            setSlaStateFilter('BREACHED');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#f87171', fontWeight: 600, textTransform: 'uppercase' }}>
            🛑 Resolution Breached
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-rose)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.resolutionSlaBreachedCount ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Missed repair deadline
          </div>
        </div>

        {/* Escalated Tier 1 (Technician / Local Staff) */}
        <div
          onClick={() => {
            setEscalationFilter('1');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(14, 165, 233, 0.4)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: 'var(--elevator-blue-primary)', fontWeight: 600, textTransform: 'uppercase' }}>
            ⚡ Tier 1 Escalated
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--elevator-blue-primary)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.escalatedTier1Count ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            On-duty staff / Tech
          </div>
        </div>

        {/* Escalated Tier 2 (Building Manager) */}
        <div
          onClick={() => {
            setEscalationFilter('2');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(168, 85, 247, 0.4)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#c084fc', fontWeight: 600, textTransform: 'uppercase' }}>
            🏢 Tier 2 Escalated
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: '#a855f7', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.escalatedTier2Count ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Building Manager notified
          </div>
        </div>

        {/* Escalated Tier 3 (Company Supervisor / Admin) */}
        <div
          onClick={() => {
            setEscalationFilter('3');
            setActiveTab('incidents');
          }}
          style={{
            background: 'var(--bg-card)',
            border: '1px solid rgba(239, 68, 68, 0.5)',
            borderRadius: '10px',
            padding: '1.1rem',
            cursor: 'pointer',
          }}
        >
          <div style={{ fontSize: '0.78rem', color: '#f87171', fontWeight: 600, textTransform: 'uppercase' }}>
            🚨 Tier 3 Escalated
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 800, color: 'var(--accent-rose)', margin: '0.3rem 0' }}>
            {summaryLoading ? '—' : summary?.escalatedTier3Count ?? 0}
          </div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
            Supervisor / Admin
          </div>
        </div>
      </div>

      {/* 5. Navigation Tab Switcher */}
      <div
        style={{
          display: 'flex',
          borderBottom: '1px solid var(--border-subtle)',
          marginBottom: '1.5rem',
          gap: '1rem',
        }}
      >
        <button
          onClick={() => setActiveTab('incidents')}
          style={{
            background: 'none',
            border: 'none',
            borderBottom: activeTab === 'incidents' ? '3px solid var(--accent-cyan)' : '3px solid transparent',
            color: activeTab === 'incidents' ? 'var(--accent-cyan)' : 'var(--text-secondary)',
            fontWeight: 700,
            fontSize: '0.98rem',
            padding: '0.65rem 1rem',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <span>⚠️ Incident Queue</span>
          <span
            style={{
              fontSize: '0.75rem',
              background: 'rgba(255, 255, 255, 0.1)',
              padding: '0.15rem 0.45rem',
              borderRadius: '10px',
            }}
          >
            {incidents.length}
          </span>
        </button>

        <button
          onClick={() => setActiveTab('fleet')}
          style={{
            background: 'none',
            border: 'none',
            borderBottom: activeTab === 'fleet' ? '3px solid var(--accent-cyan)' : '3px solid transparent',
            color: activeTab === 'fleet' ? 'var(--accent-cyan)' : 'var(--text-secondary)',
            fontWeight: 700,
            fontSize: '0.98rem',
            padding: '0.65rem 1rem',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
          }}
        >
          <span>🛗 Elevator Fleet & Safety</span>
          <span
            style={{
              fontSize: '0.75rem',
              background: 'rgba(255, 255, 255, 0.1)',
              padding: '0.15rem 0.45rem',
              borderRadius: '10px',
            }}
          >
            {elevators.length}
          </span>
        </button>
      </div>

      {/* TAB 1: INCIDENT QUEUE (Part B) */}
      {activeTab === 'incidents' && (
        <div>
          {/* Queue Filter Bar */}
          <div
            style={{
              background: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
              borderRadius: '10px',
              padding: '1.25rem',
              marginBottom: '1.5rem',
              display: 'flex',
              flexDirection: 'column',
              gap: '1rem',
            }}
          >
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem', alignItems: 'center', justifyContent: 'space-between' }}>
              {/* Search Box */}
              <div style={{ flex: '1 1 240px', minWidth: '220px' }}>
                <input
                  type="text"
                  placeholder="Search ticket, elevator, category, description..."
                  value={searchQuery}
                  onChange={(e) => {
                    setSearchQuery(e.target.value);
                    setCurrentPage(1);
                  }}
                  style={{
                    width: '100%',
                    background: 'var(--bg-dark)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '8px',
                    padding: '0.5rem 0.85rem',
                    fontSize: '0.85rem',
                    color: 'var(--text-primary)',
                  }}
                />
              </div>

              {/* Sorting Option */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Sort By:</label>
                <select
                  value={sortOption}
                  onChange={(e) => setSortOption(e.target.value)}
                  style={{
                    background: 'var(--bg-dark)',
                    color: 'var(--text-primary)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '8px',
                    padding: '0.5rem 0.75rem',
                    fontSize: '0.82rem',
                  }}
                >
                  <option value="EMERGENCY_FIRST">🚨 Emergency & Critical First</option>
                  <option value="OLDEST_UNACKNOWLEDGED">⏳ Oldest Unacknowledged First</option>
                  <option value="NEWEST_FIRST">⏱️ Most Recently Reported</option>
                  <option value="OLDEST_FIRST">📅 Oldest Reported</option>
                </select>
              </div>
            </div>

            {/* Filter Pills */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem', alignItems: 'center' }}>
              {/* Severity Filter */}
              <select
                value={severityFilter}
                onChange={(e) => {
                  setSeverityFilter(e.target.value);
                  setCurrentPage(1);
                }}
                style={{
                  background: 'var(--bg-dark)',
                  color: 'var(--text-primary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  padding: '0.45rem 0.75rem',
                  fontSize: '0.82rem',
                }}
              >
                <option value="">All Severities</option>
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>

              {/* Status Filter */}
              <select
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setCurrentPage(1);
                }}
                style={{
                  background: 'var(--bg-dark)',
                  color: 'var(--text-primary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  padding: '0.45rem 0.75rem',
                  fontSize: '0.82rem',
                }}
              >
                <option value="">All Statuses</option>
                <option value="REPORTED">REPORTED (Awaiting Ack)</option>
                <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
                <option value="ASSIGNED">ASSIGNED</option>
                <option value="IN_PROGRESS">IN_PROGRESS</option>
                <option value="RESOLVED">RESOLVED</option>
                <option value="CLOSED">CLOSED</option>
              </select>

              {/* Assigned Technician Filter */}
              <select
                value={technicianFilter}
                onChange={(e) => {
                  setTechnicianFilter(e.target.value);
                  setCurrentPage(1);
                }}
                style={{
                  background: 'var(--bg-dark)',
                  color: 'var(--text-primary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  padding: '0.45rem 0.75rem',
                  fontSize: '0.82rem',
                }}
              >
                <option value="">All Technicians</option>
                {technicians.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.fullName}
                  </option>
                ))}
              </select>

              {/* SLA State Filter (Module 10) */}
              <select
                value={slaStateFilter}
                onChange={(e) => {
                  setSlaStateFilter(e.target.value);
                  setCurrentPage(1);
                }}
                style={{
                  background: 'var(--bg-dark)',
                  color: 'var(--text-primary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  padding: '0.45rem 0.75rem',
                  fontSize: '0.82rem',
                }}
              >
                <option value="">All SLA Compliance</option>
                <option value="BREACHED">🛑 Breached SLA</option>
                <option value="APPROACHING_DEADLINE">⚠️ Approaching Deadline</option>
                <option value="ON_TRACK">⏱️ On Track</option>
                <option value="MET">✅ Met Targets</option>
              </select>

              {/* Escalation Level Filter (Module 10) */}
              <select
                value={escalationFilter}
                onChange={(e) => {
                  setEscalationFilter(e.target.value);
                  setCurrentPage(1);
                }}
                style={{
                  background: 'var(--bg-dark)',
                  color: 'var(--text-primary)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: '8px',
                  padding: '0.45rem 0.75rem',
                  fontSize: '0.82rem',
                }}
              >
                <option value="">All Escalation Tiers</option>
                <option value="0">Tier 0 (Standard)</option>
                <option value="1">Tier 1 (Technician / Local Staff)</option>
                <option value="2">Tier 2 (Building Manager)</option>
                <option value="3">Tier 3 (Company Supervisor / Admin)</option>
              </select>

              {/* Entrapment Only Toggle */}
              <label
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.4rem',
                  fontSize: '0.82rem',
                  color: entrapmentFilter ? 'var(--accent-rose)' : 'var(--text-secondary)',
                  fontWeight: entrapmentFilter ? 700 : 500,
                  cursor: 'pointer',
                  background: entrapmentFilter ? 'rgba(239, 68, 68, 0.15)' : 'var(--bg-dark)',
                  padding: '0.45rem 0.75rem',
                  borderRadius: '8px',
                  border: `1px solid ${entrapmentFilter ? 'rgba(239, 68, 68, 0.4)' : 'var(--border-subtle)'}`,
                }}
              >
                <input
                  type="checkbox"
                  checked={entrapmentFilter}
                  onChange={(e) => {
                    setEntrapmentFilter(e.target.checked);
                    setCurrentPage(1);
                  }}
                />
                🚨 Entrapment Emergencies Only
              </label>

              {/* Clear Filters Button */}
              {(severityFilter || statusFilter || entrapmentFilter || technicianFilter || slaStateFilter || escalationFilter || searchQuery) && (
                <button
                  onClick={() => {
                    setSeverityFilter('');
                    setStatusFilter('');
                    setEntrapmentFilter(false);
                    setTechnicianFilter('');
                    setSlaStateFilter('');
                    setEscalationFilter('');
                    setSearchQuery('');
                    setCurrentPage(1);
                  }}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'var(--accent-cyan)',
                    fontSize: '0.8rem',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                    padding: '0.4rem',
                  }}
                >
                  Reset Filters
                </button>
              )}
            </div>
          </div>

          {/* Queue Table or Cards */}
          {incidentsLoading ? (
            <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
              <div style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>⏳</div>
              Loading incident queue...
            </div>
          ) : incidentsError ? (
            <div
              style={{
                background: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.3)',
                padding: '1.5rem',
                borderRadius: '8px',
                color: 'var(--accent-rose)',
              }}
            >
              {incidentsError}
            </div>
          ) : processedIncidents.length === 0 ? (
            <div
              style={{
                textAlign: 'center',
                padding: '3rem',
                background: 'var(--bg-card)',
                borderRadius: '10px',
                border: '1px solid var(--border-subtle)',
              }}
            >
              <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>📭</div>
              <h3 style={{ margin: 0, fontWeight: 700 }}>No Incidents Match Selected Filters</h3>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', marginTop: '0.4rem' }}>
                There are no open or matching tickets in this building scope.
              </p>
            </div>
          ) : (
            <>
              <div
                style={{
                  background: 'var(--bg-card)',
                  borderRadius: '10px',
                  border: '1px solid var(--border-subtle)',
                  overflowX: 'auto',
                }}
              >
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem' }}>
                  <thead>
                    <tr style={{ background: 'rgba(255, 255, 255, 0.02)', borderBottom: '1px solid var(--border-subtle)' }}>
                      <th style={{ textAlign: 'left', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Ticket #</th>
                      <th style={{ textAlign: 'left', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Building & Lift</th>
                      <th style={{ textAlign: 'left', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Category & Details</th>
                      <th style={{ textAlign: 'center', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Severity</th>
                      <th style={{ textAlign: 'center', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Status</th>
                      <th style={{ textAlign: 'center', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>SLA & Tier</th>
                      <th style={{ textAlign: 'left', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Assigned Tech</th>
                      <th style={{ textAlign: 'right', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Reported</th>
                      <th style={{ textAlign: 'center', padding: '0.85rem 1rem', color: 'var(--text-secondary)' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedIncidents.map((inc) => (
                      <tr
                        key={inc.id}
                        style={{
                          borderBottom: '1px solid var(--border-subtle)',
                          background: inc.entrapmentReported ? 'rgba(239, 68, 68, 0.06)' : 'transparent',
                        }}
                      >
                        {/* Ticket # & Entrapment Badge */}
                        <td style={{ padding: '0.85rem 1rem' }}>
                          <div style={{ fontFamily: 'monospace', fontWeight: 700, color: 'var(--text-primary)' }}>
                            {inc.ticketNumber}
                          </div>
                          {inc.entrapmentReported && (
                            <span
                              style={{
                                display: 'inline-block',
                                fontSize: '0.68rem',
                                fontWeight: 800,
                                color: '#ef4444',
                                background: 'rgba(239, 68, 68, 0.15)',
                                border: '1px solid rgba(239, 68, 68, 0.35)',
                                padding: '0.1rem 0.35rem',
                                borderRadius: '4px',
                                marginTop: '0.2rem',
                              }}
                            >
                              🚨 ENTRAPMENT
                            </span>
                          )}
                        </td>

                        {/* Building & Elevator */}
                        <td style={{ padding: '0.85rem 1rem' }}>
                          <div style={{ fontWeight: 600 }}>{inc.buildingName || `Building #${inc.buildingId}`}</div>
                          <div style={{ fontSize: '0.78rem', color: 'var(--accent-cyan)' }}>
                            🛗 {inc.elevatorCode || `Lift #${inc.elevatorId}`}
                          </div>
                        </td>

                        {/* Category & Description */}
                        <td style={{ padding: '0.85rem 1rem', maxWidth: '280px' }}>
                          <div style={{ fontWeight: 600 }}>{inc.issueCategoryName}</div>
                          <div
                            style={{
                              fontSize: '0.78rem',
                              color: 'var(--text-secondary)',
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                            }}
                            title={inc.description}
                          >
                            {inc.description}
                          </div>
                        </td>

                        {/* Severity */}
                        <td style={{ padding: '0.85rem 1rem', textAlign: 'center' }}>
                          <span className={`status-badge ${getSeverityBadgeClass(inc.severity)}`}>
                            {inc.severity}
                          </span>
                        </td>

                        {/* Status */}
                        <td style={{ padding: '0.85rem 1rem', textAlign: 'center' }}>
                          <span className={`status-badge ${getStatusBadgeClass(inc.status)}`}>
                            {inc.status}
                          </span>
                        </td>

                        {/* SLA & Escalation Tier (Module 10) */}
                        <td style={{ padding: '0.85rem 1rem', textAlign: 'center' }}>
                          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.2rem' }}>
                            {/* SLA Badge */}
                            <span
                              style={{
                                fontSize: '0.68rem',
                                fontWeight: 700,
                                padding: '0.15rem 0.4rem',
                                borderRadius: '4px',
                                background: inc.responseSlaBreached || inc.resolutionSlaBreached || inc.responseSlaState === 'BREACHED' || inc.resolutionSlaState === 'BREACHED'
                                  ? 'rgba(239, 68, 68, 0.2)'
                                  : inc.responseSlaState === 'APPROACHING_DEADLINE' || inc.resolutionSlaState === 'APPROACHING_DEADLINE'
                                  ? 'rgba(245, 158, 11, 0.2)'
                                  : inc.responseSlaState === 'MET' && inc.resolutionSlaState === 'MET'
                                  ? 'rgba(16, 185, 129, 0.2)'
                                  : 'rgba(56, 189, 248, 0.15)',
                                color: inc.responseSlaBreached || inc.resolutionSlaBreached || inc.responseSlaState === 'BREACHED' || inc.resolutionSlaState === 'BREACHED'
                                  ? '#f87171'
                                  : inc.responseSlaState === 'APPROACHING_DEADLINE' || inc.resolutionSlaState === 'APPROACHING_DEADLINE'
                                  ? '#fbbf24'
                                  : inc.responseSlaState === 'MET' && inc.resolutionSlaState === 'MET'
                                  ? '#34d399'
                                  : 'var(--accent-cyan)',
                              }}
                            >
                              {inc.responseSlaBreached || inc.resolutionSlaBreached
                                ? 'BREACHED'
                                : inc.responseSlaState === 'APPROACHING_DEADLINE' || inc.resolutionSlaState === 'APPROACHING_DEADLINE'
                                ? 'APPROACHING'
                                : inc.responseSlaState || 'ON_TRACK'}
                            </span>

                            {/* Escalation Tier indicator */}
                            {inc.escalationLevel > 0 && (
                              <span
                                style={{
                                  fontSize: '0.65rem',
                                  fontWeight: 800,
                                  color: '#c084fc',
                                  background: 'rgba(168, 85, 247, 0.18)',
                                  padding: '0.1rem 0.35rem',
                                  borderRadius: '3px',
                                  border: '1px solid rgba(168, 85, 247, 0.35)',
                                }}
                              >
                                ⚡ Tier {inc.escalationLevel}
                              </span>
                            )}
                          </div>
                        </td>

                        {/* Assigned Tech */}
                        <td style={{ padding: '0.85rem 1rem' }}>
                          {inc.assignedToName ? (
                            <div>
                              <div style={{ fontWeight: 600 }}>{inc.assignedToName}</div>
                              <div style={{ fontSize: '0.74rem', color: 'var(--text-secondary)' }}>
                                {inc.assignedToEmail}
                              </div>
                            </div>
                          ) : (
                            <span style={{ color: 'var(--text-secondary)', fontStyle: 'italic', fontSize: '0.8rem' }}>
                              Unassigned
                            </span>
                          )}
                        </td>

                        {/* Reported Timestamp & Elapsed */}
                        <td style={{ padding: '0.85rem 1rem', textAlign: 'right' }}>
                          <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>
                            {formatTimeElapsed(inc.reportedAt)}
                          </div>
                          <div style={{ fontSize: '0.74rem', color: 'var(--text-secondary)' }}>
                            {new Date(inc.reportedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </div>
                        </td>

                        {/* Triage Action Button */}
                        <td style={{ padding: '0.85rem 1rem', textAlign: 'center' }}>
                          <Link
                            to={`/incidents/${inc.id}`}
                            className="btn-secondary"
                            style={{
                              padding: '0.35rem 0.75rem',
                              fontSize: '0.76rem',
                              display: 'inline-block',
                            }}
                          >
                            Triage &rarr;
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Pagination Controls */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '1rem 0.5rem',
                  fontSize: '0.82rem',
                  color: 'var(--text-secondary)',
                }}
              >
                <div>
                  Showing {Math.min(processedIncidents.length, (currentPage - 1) * pageSize + 1)}–
                  {Math.min(processedIncidents.length, currentPage * pageSize)} of {processedIncidents.length} incidents
                </div>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <button
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                    disabled={currentPage === 1}
                    className="btn-secondary"
                    style={{ padding: '0.35rem 0.7rem', fontSize: '0.78rem' }}
                  >
                    &larr; Previous
                  </button>
                  <span style={{ alignSelf: 'center', fontWeight: 600 }}>
                    Page {currentPage} of {totalPages}
                  </span>
                  <button
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                    disabled={currentPage === totalPages}
                    className="btn-secondary"
                    style={{ padding: '0.35rem 0.7rem', fontSize: '0.78rem' }}
                  >
                    Next &rarr;
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* TAB 2: ELEVATOR FLEET & SAFETY (Part C) */}
      {activeTab === 'fleet' && (
        <div>
          {/* Safety Disclaimer Notice */}
          <div
            style={{
              background: 'rgba(56, 189, 248, 0.08)',
              border: '1px solid rgba(56, 189, 248, 0.3)',
              borderRadius: '10px',
              padding: '1rem 1.25rem',
              marginBottom: '1.5rem',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.75rem',
            }}
          >
            <span style={{ fontSize: '1.3rem' }}>🛡️</span>
            <div>
              <strong style={{ color: 'var(--accent-cyan)', fontSize: '0.9rem' }}>
                Operational Safety & Physical Inspection Notice
              </strong>
              <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                Recorded software statuses represent data logged by staff and automatic incident safety interlocks.
                <strong> Software status does not substitute for certified physical on-site verification</strong>.
                Before restoring any unit from <code>OUT_OF_SERVICE</code> to <code>ACTIVE</code>, verify physical safety
                inspections according to building elevator compliance regulations.
              </p>
            </div>
          </div>

          {/* Fleet Filter Bar */}
          <div
            style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '0.75rem',
              alignItems: 'center',
              marginBottom: '1.25rem',
            }}
          >
            <label style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', fontWeight: 600 }}>
              Filter by Operational State:
            </label>
            <select
              value={elevatorStatusFilter}
              onChange={(e) => setElevatorStatusFilter(e.target.value)}
              style={{
                background: 'var(--bg-card)',
                color: 'var(--text-primary)',
                border: '1px solid var(--border-subtle)',
                borderRadius: '8px',
                padding: '0.45rem 0.75rem',
                fontSize: '0.82rem',
              }}
            >
              <option value="">All Elevators ({elevators.length})</option>
              <option value="OUT_OF_SERVICE">Out of Service Only</option>
              <option value="MAINTENANCE">Under Maintenance Only</option>
              <option value="ACTIVE">Active in Service Only</option>
            </select>
          </div>

          {/* Elevators Grid / Table */}
          {elevatorsLoading ? (
            <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-secondary)' }}>
              Loading elevator fleet...
            </div>
          ) : elevatorsError ? (
            <div
              style={{
                background: 'rgba(239, 68, 68, 0.1)',
                border: '1px solid rgba(239, 68, 68, 0.3)',
                padding: '1.5rem',
                borderRadius: '8px',
                color: 'var(--accent-rose)',
              }}
            >
              {elevatorsError}
            </div>
          ) : elevators.length === 0 ? (
            <div
              style={{
                textAlign: 'center',
                padding: '3rem',
                background: 'var(--bg-card)',
                borderRadius: '10px',
                border: '1px solid var(--border-subtle)',
              }}
            >
              <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>🛗</div>
              <h3 style={{ margin: 0, fontWeight: 700 }}>No Elevators Found</h3>
              <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', marginTop: '0.4rem' }}>
                No elevators registered under this selection.
              </p>
            </div>
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))',
                gap: '1.25rem',
              }}
            >
              {elevators.map((elv) => {
                const statusMeta = getElevatorStatusBadge(elv.operationalStatus);
                const isOutOfService = elv.operationalStatus === 'OUT_OF_SERVICE';
                const isMaintenance = elv.operationalStatus === 'MAINTENANCE';

                return (
                  <div
                    key={elv.id}
                    style={{
                      background: 'var(--bg-card)',
                      border: `1px solid ${
                        isOutOfService
                          ? 'rgba(239, 68, 68, 0.4)'
                          : isMaintenance
                          ? 'rgba(245, 158, 11, 0.4)'
                          : 'var(--border-subtle)'
                      }`,
                      borderRadius: '10px',
                      padding: '1.25rem',
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between',
                      boxShadow: isOutOfService ? '0 0 12px rgba(239, 68, 68, 0.15)' : 'none',
                    }}
                  >
                    <div>
                      {/* Top Bar: Code and Status Badge */}
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                          <span style={{ fontSize: '1.25rem' }}>🛗</span>
                          <span style={{ fontSize: '1.1rem', fontWeight: 800, color: 'var(--text-primary)' }}>
                            {elv.elevatorCode}
                          </span>
                        </div>
                        <span
                          style={{
                            fontSize: '0.72rem',
                            fontWeight: 700,
                            padding: '0.2rem 0.55rem',
                            borderRadius: '6px',
                            color: statusMeta.color,
                            background: statusMeta.bg,
                            border: `1px solid ${statusMeta.border}`,
                          }}
                        >
                          {statusMeta.label}
                        </span>
                      </div>

                      {/* Building & Details */}
                      <div style={{ fontSize: '0.84rem', color: 'var(--text-primary)', marginBottom: '0.4rem' }}>
                        🏢 <strong>{elv.buildingName || `Building #${elv.buildingId}`}</strong>
                        {elv.buildingCode && ` (${elv.buildingCode})`}
                      </div>

                      <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '0.4rem' }}>
                        📍 {elv.locationDescription || 'Location not specified'}
                      </div>

                      <div style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', marginBottom: '0.2rem' }}>
                        Floors: <strong>{elv.totalFloors || '—'}</strong> &bull; Model:{' '}
                        <strong>{elv.model || 'Standard'}</strong>
                      </div>

                      {elv.serviceProvider && (
                        <div style={{ fontSize: '0.76rem', color: 'var(--text-secondary)' }}>
                          Service Provider: <strong>{elv.serviceProvider}</strong>
                        </div>
                      )}
                    </div>

                    {/* Action Bar */}
                    <div
                      style={{
                        marginTop: '1.25rem',
                        paddingTop: '0.75rem',
                        borderTop: '1px solid var(--border-subtle)',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: '0.5rem',
                      }}
                    >
                      {/* Button to view incidents for this elevator */}
                      <button
                        onClick={() => {
                          setSearchQuery(elv.elevatorCode);
                          setActiveTab('incidents');
                        }}
                        className="btn-secondary"
                        style={{ padding: '0.35rem 0.65rem', fontSize: '0.76rem' }}
                      >
                        View Incidents
                      </button>

                      {/* Admin/Manager/Tech button to update status */}
                      {canModifyElevatorStatus && (
                        <button
                          onClick={() => handleOpenStatusModal(elv)}
                          className="btn-secondary"
                          style={{
                            padding: '0.35rem 0.65rem',
                            fontSize: '0.76rem',
                            borderColor: 'var(--accent-cyan)',
                            color: 'var(--accent-cyan)',
                          }}
                        >
                          Change Status
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {/* 6. Elevator Status Override Modal (RBAC Authorized) */}
      {selectedElevatorForStatus && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 1000,
            padding: '1rem',
          }}
        >
          <div
            style={{
              background: 'var(--bg-card)',
              border: '1px solid var(--border-subtle)',
              borderRadius: '12px',
              maxWidth: '460px',
              width: '100%',
              padding: '1.75rem',
              boxShadow: '0 20px 40px rgba(0, 0, 0, 0.5)',
            }}
          >
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.2rem', fontWeight: 800 }}>
              Update Operational Status
            </h3>
            <p style={{ margin: '0 0 1.25rem', fontSize: '0.84rem', color: 'var(--text-secondary)' }}>
              Elevator <strong>{selectedElevatorForStatus.elevatorCode}</strong> ({selectedElevatorForStatus.buildingName})
            </p>

            {statusModalError && (
              <div
                style={{
                  background: 'rgba(239, 68, 68, 0.15)',
                  border: '1px solid rgba(239, 68, 68, 0.3)',
                  padding: '0.75rem',
                  borderRadius: '6px',
                  color: 'var(--accent-rose)',
                  fontSize: '0.82rem',
                  marginBottom: '1rem',
                }}
              >
                {statusModalError}
              </div>
            )}

            <form onSubmit={handleSaveElevatorStatus}>
              <div style={{ marginBottom: '1.25rem' }}>
                <label style={{ display: 'block', fontSize: '0.82rem', fontWeight: 600, marginBottom: '0.4rem' }}>
                  Select Recorded Operational Status:
                </label>
                <select
                  value={newOperationalStatus}
                  onChange={(e) => setNewOperationalStatus(e.target.value)}
                  style={{
                    width: '100%',
                    background: 'var(--bg-dark)',
                    color: 'var(--text-primary)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: '8px',
                    padding: '0.6rem 0.85rem',
                    fontSize: '0.9rem',
                  }}
                  required
                >
                  <option value="ACTIVE">ACTIVE — Normal In-Service Operation</option>
                  <option value="MAINTENANCE">MAINTENANCE — Scheduled Servicing / Inspection</option>
                  <option value="OUT_OF_SERVICE">OUT_OF_SERVICE — Safety Shutdown / Defect</option>
                </select>
              </div>

              <div
                style={{
                  background: 'rgba(245, 158, 11, 0.1)',
                  border: '1px solid rgba(245, 158, 11, 0.3)',
                  padding: '0.75rem',
                  borderRadius: '6px',
                  fontSize: '0.78rem',
                  color: 'var(--accent-amber)',
                  marginBottom: '1.25rem',
                }}
              >
                ⚠️ Note: This records administrative status in the software registry. Ensure physical inspection
                protocols have verified the cabin safety condition.
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                <button
                  type="button"
                  onClick={() => setSelectedElevatorForStatus(null)}
                  disabled={statusModalLoading}
                  className="btn-secondary"
                  style={{ padding: '0.5rem 1rem' }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={statusModalLoading}
                  className="btn-primary"
                  style={{ padding: '0.5rem 1.25rem' }}
                >
                  {statusModalLoading ? 'Updating...' : 'Save Status'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
