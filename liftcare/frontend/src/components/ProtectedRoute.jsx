import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ProtectedRoute({ children, allowedRoles }) {
  const { isAuthenticated, user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '5rem 1rem' }}>
        <div className="status-indicator-dot checking" style={{ width: '20px', height: '20px', margin: '0 auto 1rem' }}></div>
        <p style={{ color: 'var(--text-secondary)' }}>Verifying local authentication...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && (!user || !allowedRoles.includes(user.role))) {
    return (
      <div className="status-card disconnected" style={{ maxWidth: '600px', margin: '3rem auto', textAlign: 'center' }}>
        <h2 style={{ color: 'var(--accent-rose)', marginBottom: '0.5rem' }}>Access Denied</h2>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '1.5rem' }}>
          Your account role (<code style={{ color: 'var(--accent-amber)' }}>{user?.role}</code>) lacks authorization for this view.
        </p>
        <Navigate to="/dashboard" replace />
      </div>
    );
  }

  return children;
}
