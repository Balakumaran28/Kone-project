import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { checkBackendHealth } from '../services/api';
import { getPendingActionCount } from '../services/offlineQueue';
import { ElevatorIcon } from './ElevatorIcons';

export default function PortalHeader() {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [healthStatus, setHealthStatus] = useState(null);

  useEffect(() => {
    let mounted = true;

    const probe = async () => {
      try {
        const health = await checkBackendHealth(3000).catch(() => null);
        if (mounted) setHealthStatus(health);
      } catch { /* silent */ }
    };

    probe();
    const id = setInterval(probe, 8000);
    return () => { mounted = false; clearInterval(id); };
  }, [user]);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const isReachable = healthStatus?.reachable;
  const isDbOk      = healthStatus?.data?.database?.status === 'CONNECTED';
  const dbName      = healthStatus?.data?.database?.databaseProductName || null;

  const dotClass = isReachable && isDbOk ? 'online' : isReachable ? 'warning' : 'offline';
  const nodeLabel = isReachable ? 'Connected' : 'Offline';

  return (
    <header className="portal-header">
      <div className="portal-header-inner">
        {/* Brand */}
        <Link to={isAuthenticated ? '/dashboard' : '/'} className="portal-brand">
          <div className="portal-brand-mark">
            <ElevatorIcon size={17} color="#ffffff" />
          </div>
          <span className="portal-brand-name">LiftCare</span>
        </Link>

        {/* Node status */}
        <div className="portal-node-status">
          <span className={`status-dot ${dotClass}`} />
          <span>Local Node: <strong>{nodeLabel}</strong></span>
          {isReachable && dbName && (
            <>
              <span style={{ color: 'var(--border-strong)', margin: '0 0.25rem' }}>·</span>
              <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                DB: <strong>{dbName}</strong>
              </span>
            </>
          )}
        </div>

        {/* Right cluster */}
        <div className="portal-header-right">
          {isAuthenticated ? (
            <>
              <div className="portal-user-pill">
                <div className="portal-user-avatar">
                  {user?.fullName ? user.fullName.charAt(0).toUpperCase() : 'U'}
                </div>
                <div>
                  <div className="portal-user-name">{user?.fullName || 'User'}</div>
                  <div className="portal-user-role">{user?.role?.replace(/_/g, ' ')}</div>
                </div>
              </div>
              <button
                onClick={handleLogout}
                className="btn btn-secondary btn-sm"
              >
                Sign Out
              </button>
            </>
          ) : (
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <Link to="/login"    className="btn btn-secondary btn-sm">Sign In</Link>
              <Link to="/register" className="btn btn-primary btn-sm">Register</Link>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
