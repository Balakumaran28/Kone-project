import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getPendingActionCount } from '../services/offlineQueue';
import {
  DashboardIcon,
  ControlRoomIcon,
  IncidentIcon,
  BuildingIcon,
  QueueIcon,
  SyncIcon,
  FleetIcon,
  MaintenanceIcon,
} from './ElevatorIcons';

export default function Sidebar() {
  const { user } = useAuth();
  const [pendingCount, setPendingCount] = useState(0);

  const isResident           = user?.role === 'RESIDENT';
  const isSupervisorOrAdmin  = ['ADMINISTRATOR', 'COMPANY_SUPERVISOR'].includes(user?.role);

  useEffect(() => {
    let mounted = true;
    const load = async () => {
      try {
        const count = await getPendingActionCount(user?.email || null);
        if (mounted) setPendingCount(count);
      } catch { /* ignore */ }
    };
    load();
    const id = setInterval(load, 10000);
    return () => { mounted = false; clearInterval(id); };
  }, [user]);

  return (
    <aside className="app-sidebar">
      <nav className="sidebar-nav" aria-label="Portal navigation">
        <div className="sidebar-group-label">Operations</div>

        <NavLink
          to="/dashboard"
          className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
        >
          <span className="sidebar-link-icon"><DashboardIcon size={16} /></span>
          Dashboard
        </NavLink>

        {!isResident && (
          <NavLink
            to="/control-room"
            className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
          >
            <span className="sidebar-link-icon"><ControlRoomIcon size={16} /></span>
            Control Room
          </NavLink>
        )}

        <NavLink
          to="/incidents"
          className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
        >
          <span className="sidebar-link-icon"><IncidentIcon size={16} /></span>
          Incidents
        </NavLink>

        <NavLink
          to="/incidents/report"
          className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
        >
          <span className="sidebar-link-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </span>
          Report Issue
        </NavLink>

        <div className="sidebar-group-label" style={{ marginTop: '0.5rem' }}>
          Fleet &amp; Assets
        </div>

        <NavLink
          to="/buildings"
          className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
        >
          <span className="sidebar-link-icon"><BuildingIcon size={16} /></span>
          Buildings &amp; Elevators
        </NavLink>

        <div className="sidebar-group-label" style={{ marginTop: '0.5rem' }}>
          System
        </div>

        <NavLink
          to="/offline-queue"
          className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
        >
          <span className="sidebar-link-icon"><QueueIcon size={16} /></span>
          Offline Queue
          {pendingCount > 0 && (
            <span className="sidebar-badge">{pendingCount}</span>
          )}
        </NavLink>

        {!isResident && (
          <NavLink
            to="/cloud-sync"
            className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
          >
            <span className="sidebar-link-icon"><SyncIcon size={16} /></span>
            Cloud Sync
          </NavLink>
        )}

        {isSupervisorOrAdmin && (
          <NavLink
            to="/central-dashboard"
            className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
          >
            <span className="sidebar-link-icon"><FleetIcon size={16} /></span>
            Central Fleet
          </NavLink>
        )}
      </nav>

      <div className="sidebar-footer">
        <div style={{ marginBottom: '0.1rem', color: 'rgba(255,255,255,0.5)', fontWeight: 600 }}>Local Node</div>
        <code style={{ fontSize: '0.7rem' }}>building-local-node-01</code>
      </div>
    </aside>
  );
}
