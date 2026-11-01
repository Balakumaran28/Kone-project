import React, { useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { ElevatorIcon } from './ElevatorIcons';

export default function PublicHeader() {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="pub-header">
      <div className="pub-header-inner">
        {/* Brand */}
        <Link to="/" className="pub-brand">
          <div className="pub-brand-mark">
            <ElevatorIcon size={18} color="#ffffff" />
          </div>
          <div>
            <div className="pub-brand-name">LiftCare</div>
            <div className="pub-brand-sub">Elevator Management</div>
          </div>
        </Link>

        {/* Desktop navigation — links scroll to sections on the homepage */}
        <nav className="pub-nav" aria-label="Main navigation">
          <a href="#services" className="pub-nav-link">Services</a>
          <a href="#maintenance" className="pub-nav-link">Maintenance</a>
          <a href="#reliability" className="pub-nav-link">Reliability</a>
          <a href="#about" className="pub-nav-link">About</a>
        </nav>

        {/* CTA */}
        <div className="pub-header-cta">
          <Link to="/login" className="btn btn-outline-white btn-sm">
            Building Portal
          </Link>
        </div>

        {/* Mobile hamburger */}
        <button
          className="pub-hamburger"
          aria-label="Toggle navigation menu"
          onClick={() => setMobileOpen((v) => !v)}
        >
          {mobileOpen ? '✕' : '≡'}
        </button>
      </div>

      {/* Mobile nav drawer */}
      <div className={`pub-mobile-nav ${mobileOpen ? 'open' : ''}`}>
        <a href="#services"     className="pub-nav-link" onClick={() => setMobileOpen(false)}>Services</a>
        <a href="#maintenance"  className="pub-nav-link" onClick={() => setMobileOpen(false)}>Maintenance</a>
        <a href="#reliability"  className="pub-nav-link" onClick={() => setMobileOpen(false)}>Reliability</a>
        <a href="#about"        className="pub-nav-link" onClick={() => setMobileOpen(false)}>About</a>
        <Link to="/login"       className="pub-nav-link" onClick={() => setMobileOpen(false)}>Building Portal →</Link>
      </div>
    </header>
  );
}
