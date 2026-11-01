import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ElevatorIcon } from '../components/ElevatorIcons';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const from = location.state?.from?.pathname || '/dashboard';

  const demoAccounts = [
    { label: 'System Admin', email: 'admin@liftcare.com', role: 'ADMINISTRATOR' },
    { label: 'Building Manager', email: 'manager@liftcare.com', role: 'BUILDING_MANAGER' },
    { label: 'Lead Technician', email: 'tech@liftcare.com', role: 'TECHNICIAN' },
    { label: 'Security Desk', email: 'security@liftcare.com', role: 'SECURITY_STAFF' },
    { label: 'Resident', email: 'resident@liftcare.com', role: 'RESIDENT' },
  ];

  const handleQuickFill = (demoEmail) => {
    setEmail(demoEmail);
    setPassword('Password123!');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!email.trim() || !password) {
      setError('Please provide both your account email and password.');
      return;
    }

    setLoading(true);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message || 'Authentication failed. Please verify your local node credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '460px', margin: '3rem auto', padding: '0 1rem' }}>
      <div className="elev-card" style={{ padding: '2.25rem 2rem' }}>
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div
            className="brand-symbol"
            style={{ width: '48px', height: '48px', margin: '0 auto 0.75rem', borderRadius: 'var(--r-md)' }}
          >
            <ElevatorIcon size={26} color="#ffffff" />
          </div>
          <h1 style={{ fontSize: '1.45rem', fontWeight: 800, color: 'var(--brand-primary)', letterSpacing: '-0.02em', margin: 0 }}>
            Building Portal Sign In
          </h1>
          <p style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>
            LiftCare Building Operations
          </p>
        </div>

        {error && (
          <div
            style={{
              padding: '0.75rem 1rem',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--safety-red-bg)',
              border: '1px solid var(--safety-red-border)',
              color: 'var(--safety-red)',
              fontSize: '0.82rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              marginBottom: '1.25rem',
            }}
          >
            <span>⚠️</span>
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="email">
              Account Email <span className="required">*</span>
            </label>
            <input
              id="email"
              type="email"
              required
              autoFocus
              className="form-input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="e.g. manager@liftcare.com"
            />
          </div>

          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
            <label className="form-label" htmlFor="password">
              Password <span className="required">*</span>
            </label>
            <input
              id="password"
              type="password"
              required
              className="form-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="btn btn-primary"
            style={{ width: '100%', padding: '0.65rem' }}
          >
            {loading ? 'Authenticating Local Session...' : 'Sign In to Operations Portal'}
          </button>
        </form>

        {/* Demo Account Quick-Fill Section */}
        <div style={{ marginTop: '1.75rem', paddingTop: '1.25rem', borderTop: '1px solid var(--border-light)' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
            <span style={{ fontSize: '0.72rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--text-muted)' }}>
              Demo Evaluator Profiles
            </span>
            <span style={{ fontSize: '0.7rem', color: 'var(--elevator-blue-primary)' }}>One-click login</span>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
            {demoAccounts.map((acc) => (
              <button
                key={acc.email}
                type="button"
                onClick={() => handleQuickFill(acc.email)}
                className="btn btn-secondary btn-sm"
                style={{ fontSize: '0.72rem', padding: '0.25rem 0.55rem' }}
                title={`Click to fill credentials for ${acc.role}`}
              >
                {acc.label}
              </button>
            ))}
          </div>
        </div>

        <div style={{ marginTop: '1.25rem', textAlign: 'center', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
          Need resident access?{' '}
          <Link to="/register" style={{ color: 'var(--brand-action)', fontWeight: 600 }}>
            Register Resident Account
          </Link>
        </div>
      </div>
    </div>
  );
}
