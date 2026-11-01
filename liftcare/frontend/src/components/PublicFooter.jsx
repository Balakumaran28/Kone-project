import React from 'react';
import { Link } from 'react-router-dom';
import { ElevatorIcon } from './ElevatorIcons';

export default function PublicFooter() {
  return (
    <footer className="pub-footer">
      <div className="pub-footer-inner">
        <div className="pub-footer-top">
          {/* Brand column */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.75rem' }}>
              <div style={{
                width: 30, height: 30,
                background: 'rgba(255,255,255,0.12)',
                border: '1px solid rgba(255,255,255,0.2)',
                borderRadius: 4,
                display: 'flex', alignItems: 'center', justifyContent: 'center'
              }}>
                <ElevatorIcon size={16} color="#ffffff" />
              </div>
              <div className="pub-footer-brand-name">LiftCare</div>
            </div>
            <div className="pub-footer-brand-desc">
              Building-level elevator issue management and automatic escalation system.
              Designed for local deployment within the building network.
            </div>
          </div>

          {/* Platform links */}
          <div>
            <div className="pub-footer-col-title">Platform</div>
            <Link to="/login"    className="pub-footer-link">Building Portal</Link>
            <Link to="/register" className="pub-footer-link">Resident Registration</Link>
            <Link to="/incidents/report" className="pub-footer-link">Report an Issue</Link>
          </div>

          {/* System links */}
          <div>
            <div className="pub-footer-col-title">System</div>
            <a href="#services"    className="pub-footer-link">Services</a>
            <a href="#reliability" className="pub-footer-link">Reliability</a>
            <a href="#about"       className="pub-footer-link">About</a>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="pub-footer-bottom">
          <span>© {new Date().getFullYear()} LiftCare — Building Operations Platform</span>
          <span>Local Node Deployment</span>
        </div>

        <div className="pub-footer-disclaimer">
          LiftCare manages elevator issue records in software. It does not interface directly with elevator hardware, physical sensors, or emergency-dispatch services. Emergency situations require direct contact with building emergency services.
        </div>
      </div>
    </footer>
  );
}
