import React from 'react';

export default function PortalFooter() {
  return (
    <footer className="footer">
      <div className="footer-inner">
        <span>LiftCare Elevator Operations Platform &mdash; Local Node</span>
        <span style={{ fontSize: '0.73rem', color: 'var(--text-muted)' }}>
          Status reflects software records. Does not represent physical hardware telemetry.
        </span>
      </div>
    </footer>
  );
}
