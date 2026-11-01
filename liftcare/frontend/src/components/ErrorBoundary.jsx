import React from 'react';

/**
 * Resilient Error Boundary preventing full-screen whiteouts in case of unexpected errors.
 * Preserves access to basic navigation and recovery controls.
 */
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('Unhandled LiftCare frontend error:', error, errorInfo);
  }

  handleReload = () => {
    window.location.reload();
  };

  handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.href = '/';
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            maxWidth: '640px',
            margin: '4rem auto',
            padding: '2rem',
            background: 'var(--bg-card, #182238)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '12px',
            textAlign: 'center',
            color: 'var(--text-primary, #f8fafc)',
            boxShadow: '0 10px 30px rgba(0, 0, 0, 0.5)',
          }}
        >
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>⚠️</div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 800, color: 'var(--accent-rose, #ef4444)', marginBottom: '0.75rem' }}>
            Application Error Encountered
          </h2>
          <p style={{ color: 'var(--text-secondary, #94a3b8)', fontSize: '0.88rem', lineHeight: 1.6, marginBottom: '1.5rem' }}>
            A client-side error occurred while rendering this view. The local building server and background services
            remain unaffected.
          </p>

          {this.state.error && (
            <div
              style={{
                background: 'rgba(15, 23, 42, 0.8)',
                padding: '0.75rem 1rem',
                borderRadius: '8px',
                fontSize: '0.78rem',
                fontFamily: 'monospace',
                color: '#fca5a5',
                textAlign: 'left',
                overflowX: 'auto',
                marginBottom: '1.5rem',
              }}
            >
              {this.state.error.message || String(this.state.error)}
            </div>
          )}

          <div style={{ display: 'flex', gap: '0.75rem', justifyContent: 'center' }}>
            <button
              onClick={this.handleReload}
              style={{
                padding: '0.6rem 1.25rem',
                borderRadius: '8px',
                background: 'var(--accent-cyan, #0ea5e9)',
                color: '#fff',
                border: 'none',
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              Reload Page
            </button>
            <button
              onClick={this.handleReset}
              style={{
                padding: '0.6rem 1.25rem',
                borderRadius: '8px',
                background: 'transparent',
                color: 'var(--text-secondary, #94a3b8)',
                border: '1px solid var(--border-subtle, #243252)',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              Return to Home
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
