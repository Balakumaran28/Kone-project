import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { evaluateSystemConnectivity } from '../services/api';
import {
  ElevatorIcon,
  BuildingIcon,
  IncidentIcon,
  MaintenanceIcon,
  CheckCircleIcon,
  SyncIcon,
  ControlRoomIcon,
} from '../components/ElevatorIcons';

/* ──────────────────────────────────────────────────────────────
   SVG Elevator Shaft Illustration (decorative, right side)
   ────────────────────────────────────────────────────────────── */
function ShaftIllustration() {
  return (
    <svg
      width="260"
      height="480"
      viewBox="0 0 260 480"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      {/* Shaft structure */}
      <rect x="60" y="0" width="140" height="480" fill="none" stroke="rgba(255,255,255,0.15)" strokeWidth="2" />

      {/* Guide rails */}
      <line x1="80" y1="0" x2="80" y2="480" stroke="rgba(255,255,255,0.12)" strokeWidth="3" />
      <line x1="180" y1="0" x2="180" y2="480" stroke="rgba(255,255,255,0.12)" strokeWidth="3" />

      {/* Floor guides */}
      {[80,160,240,320,400].map(y => (
        <line key={y} x1="60" y1={y} x2="200" y2={y} stroke="rgba(255,255,255,0.06)" strokeWidth="1" strokeDasharray="4 4" />
      ))}

      {/* Elevator car */}
      <rect x="82" y="180" width="96" height="120" rx="2" fill="rgba(255,255,255,0.08)" stroke="rgba(255,255,255,0.3)" strokeWidth="1.5" />

      {/* Car door seam */}
      <line x1="130" y1="180" x2="130" y2="300" stroke="rgba(255,255,255,0.2)" strokeWidth="1" />

      {/* Door handles */}
      <rect x="122" y="234" width="5" height="12" rx="2" fill="rgba(255,255,255,0.4)" />
      <rect x="133" y="234" width="5" height="12" rx="2" fill="rgba(255,255,255,0.4)" />

      {/* Hoist rope */}
      <line x1="100" y1="0" x2="100" y2="185" stroke="rgba(255,255,255,0.2)" strokeWidth="1.5" />
      <line x1="130" y1="0" x2="130" y2="180" stroke="rgba(255,255,255,0.2)" strokeWidth="2" />
      <line x1="160" y1="0" x2="160" y2="185" stroke="rgba(255,255,255,0.2)" strokeWidth="1.5" />

      {/* Counterweight */}
      <rect x="195" y="100" width="20" height="50" rx="2" fill="rgba(255,255,255,0.12)" stroke="rgba(255,255,255,0.2)" strokeWidth="1" />

      {/* Position indicator */}
      <circle cx="200" cy="240" r="4" fill="rgba(138,180,224,0.8)" />
    </svg>
  );
}

/* ──────────────────────────────────────────────────────────────
   Service icons (line, on tinted background)
   ────────────────────────────────────────────────────────────── */
function ServiceIcon({ children }) {
  return (
    <div className="service-icon-wrap">
      {children}
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Pillar icon wrapper
   ────────────────────────────────────────────────────────────── */
function PillarIcon({ children }) {
  return (
    <div className="pillar-icon">
      {children}
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Node status badge (shown in hero metrics strip)
   ────────────────────────────────────────────────────────────── */
function NodeStatusBadge({ connectivity, loading }) {
  if (loading) {
    return (
      <div style={{ fontSize: '0.78rem', color: 'rgba(255,255,255,0.5)' }}>
        Checking local node…
      </div>
    );
  }

  const reachable  = connectivity?.localServer?.reachable;
  const dbOk       = connectivity?.localServer?.data?.database?.status === 'CONNECTED';
  const dbName     = connectivity?.localServer?.data?.database?.databaseProductName;

  const dotColor = reachable && dbOk ? '#4ade80' : reachable ? '#facc15' : '#f87171';
  const label    = reachable ? 'Local node reachable' : 'Local node offline';

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.78rem', color: 'rgba(255,255,255,0.65)' }}>
      <span style={{ width: 8, height: 8, borderRadius: '50%', background: dotColor, display: 'inline-block', flexShrink: 0 }} />
      <span>{label}{dbName ? ` · DB: ${dbName}` : ''}</span>
    </div>
  );
}

/* ──────────────────────────────────────────────────────────────
   Main Page
   ────────────────────────────────────────────────────────────── */
export default function LandingPage() {
  const { isAuthenticated } = useAuth();
  const [connectivity, setConnectivity] = useState(null);
  const [connLoading, setConnLoading] = useState(true);

  const probe = useCallback(async () => {
    setConnLoading(true);
    try {
      const result = await evaluateSystemConnectivity();
      setConnectivity(result);
    } catch { /* silent */ }
    setConnLoading(false);
  }, []);

  useEffect(() => { probe(); }, [probe]);

  return (
    <div>
      {/* ──────────────────────────────────────────────────────
          HERO SECTION
      ────────────────────────────────────────────────────── */}
      <section className="hero" aria-labelledby="hero-heading">
        {/* Architectural background */}
        <div className="hero-bg">
          <div className="hero-bg-lines" />
          <div className="hero-bg-gradient" />
        </div>

        {/* Shaft illustration — purely decorative */}
        <div className="hero-illustration" aria-hidden="true">
          <ShaftIllustration />
        </div>

        {/* Hero text */}
        <div className="hero-content">
          <div className="hero-eyebrow">
            <ElevatorIcon size={12} color="rgba(255,255,255,0.65)" />
            Elevator Operations Platform
          </div>

          <h1 id="hero-heading" className="hero-heading">
            Elevator Reliability.<br />
            <em>Built Around You.</em>
          </h1>

          <p className="hero-sub">
            Manage elevator issues, coordinate maintenance, and respond to incidents
            through one building-focused operations platform.
          </p>

          <div className="hero-actions">
            {isAuthenticated ? (
              <Link to="/dashboard" className="btn btn-primary btn-lg">
                Open Operations Portal
              </Link>
            ) : (
              <>
                <Link to="/login" className="btn btn-primary btn-lg">
                  Access Building Portal
                </Link>
                <Link to="/login" className="btn btn-outline-white btn-lg">
                  Report an Elevator Issue
                </Link>
              </>
            )}
          </div>

          {/* Live node status */}
          <div style={{ marginTop: '2.5rem' }}>
            <NodeStatusBadge connectivity={connectivity} loading={connLoading} />
          </div>
        </div>

        {/* Metrics strip */}
        <div className="hero-metrics-strip">
          <div className="hero-metrics-inner">
            <div className="hero-metric">
              <div className="hero-metric-label">Deployment Model</div>
              <div className="hero-metric-value" style={{ fontSize: '1rem', paddingTop: '0.2rem' }}>Building-local</div>
            </div>
            <div className="hero-metric">
              <div className="hero-metric-label">Internet Dependency</div>
              <div className="hero-metric-value" style={{ fontSize: '1rem', paddingTop: '0.2rem' }}>None required</div>
            </div>
            <div className="hero-metric">
              <div className="hero-metric-label">Escalation Engine</div>
              <div className="hero-metric-value" style={{ fontSize: '1rem', paddingTop: '0.2rem' }}>Configurable SLA</div>
            </div>
            <div className="hero-metric">
              <div className="hero-metric-label">Multi-building</div>
              <div className="hero-metric-value" style={{ fontSize: '1rem', paddingTop: '0.2rem' }}>Cloud coordination</div>
            </div>
          </div>
        </div>
      </section>

      {/* ──────────────────────────────────────────────────────
          SERVICES SECTION
      ────────────────────────────────────────────────────── */}
      <section id="services" aria-labelledby="services-heading">
        <div className="pub-section">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '4rem', alignItems: 'start' }}>
            {/* Left: heading */}
            <div style={{ position: 'sticky', top: '80px' }}>
              <div className="pub-section-label">Services</div>
              <h2 id="services-heading" className="pub-section-heading">
                Everything your building needs, in one place.
              </h2>
              <p className="pub-section-body" style={{ marginTop: '1rem' }}>
                LiftCare brings together incident reporting, maintenance coordination,
                escalation, and building-wide oversight into a single operational platform.
              </p>
              {!isAuthenticated && (
                <div style={{ marginTop: '1.75rem' }}>
                  <Link to="/login" className="btn btn-primary">
                    Open Building Portal
                  </Link>
                </div>
              )}
            </div>

            {/* Right: service items */}
            <div className="services-grid">
              <div className="service-item">
                <ServiceIcon>
                  <IncidentIcon size={22} color="var(--brand-action)" />
                </ServiceIcon>
                <div className="service-title">Elevator Issue Management</div>
                <div className="service-desc">
                  Submit and track elevator fault reports, from minor defects to emergency
                  entrapments. Every report is recorded, timestamped, and traceable through
                  a full audit history.
                </div>
              </div>

              <div className="service-item" id="maintenance">
                <ServiceIcon>
                  <MaintenanceIcon size={22} color="var(--brand-action)" />
                </ServiceIcon>
                <div className="service-title">Maintenance Coordination</div>
                <div className="service-desc">
                  Assign technicians, log diagnostic findings, and manage elevator status
                  transitions. Track maintenance activity across all shafts in one place.
                </div>
              </div>

              <div className="service-item">
                <ServiceIcon>
                  <ControlRoomIcon size={22} color="var(--brand-action)" />
                </ServiceIcon>
                <div className="service-title">Emergency Incident Handling</div>
                <div className="service-desc">
                  Passenger entrapment reports are prioritised automatically, locked to
                  critical severity, and immediately visible to building security and
                  control-room staff.
                </div>
              </div>

              <div className="service-item">
                <ServiceIcon>
                  <BuildingIcon size={22} color="var(--brand-action)" />
                </ServiceIcon>
                <div className="service-title">Building Operations &amp; Escalation</div>
                <div className="service-desc">
                  Configurable SLA policies trigger automatic escalation when incidents
                  approach or breach response deadlines, ensuring the right people are
                  notified at the right time.
                </div>
              </div>

              <div className="service-item">
                <ServiceIcon>
                  <ElevatorIcon size={22} color="var(--brand-action)" />
                </ServiceIcon>
                <div className="service-title">Fleet Status Visibility</div>
                <div className="service-desc">
                  Monitor operational, maintenance, and out-of-service status across your
                  elevator fleet at building level or, when connected, across multiple sites.
                </div>
              </div>

              <div className="service-item">
                <ServiceIcon>
                  <SyncIcon size={22} color="var(--brand-action)" />
                </ServiceIcon>
                <div className="service-title">Offline-First Resilience</div>
                <div className="service-desc">
                  Incident reporting and local operations continue without internet access.
                  When connectivity resumes, pending records synchronize automatically
                  without data loss.
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <hr className="pub-divider" />

      {/* ──────────────────────────────────────────────────────
          RELIABILITY SECTION
      ────────────────────────────────────────────────────── */}
      <section id="reliability" className="reliability-section" aria-labelledby="reliability-heading">
        <div className="reliability-inner">
          {/* Left: pillars */}
          <div className="reliability-pillars">
            <div className="reliability-pillar">
              <div className="pillar-icon">
                <ElevatorIcon size={18} color="rgba(255,255,255,0.9)" />
              </div>
              <div>
                <div className="pillar-title">Local-first operations</div>
                <div className="pillar-desc">
                  Incident reporting, triage, and audit logging run entirely on the building server.
                  No internet connection is required for day-to-day operations.
                </div>
              </div>
            </div>

            <div className="reliability-pillar">
              <div className="pillar-icon">
                <ControlRoomIcon size={18} color="rgba(255,255,255,0.9)" />
              </div>
              <div>
                <div className="pillar-title">Building-level visibility</div>
                <div className="pillar-desc">
                  Control-room staff and building managers see the full operational picture —
                  active incidents, fleet status, and escalation state — in real time.
                </div>
              </div>
            </div>

            <div className="reliability-pillar">
              <div className="pillar-icon">
                <IncidentIcon size={18} color="rgba(255,255,255,0.9)" />
              </div>
              <div>
                <div className="pillar-title">Configurable SLA escalation</div>
                <div className="pillar-desc">
                  Response and resolution deadlines are set per severity level and per building.
                  The engine evaluates incidents automatically and escalates when thresholds are missed.
                </div>
              </div>
            </div>

            <div className="reliability-pillar">
              <div className="pillar-icon">
                <SyncIcon size={18} color="rgba(255,255,255,0.9)" />
              </div>
              <div>
                <div className="pillar-title">Cloud coordination when needed</div>
                <div className="pillar-desc">
                  When an internet connection is available, authorized building servers can synchronize
                  records with a central coordinator for multi-building oversight.
                  The local server never depends on this connection.
                </div>
              </div>
            </div>
          </div>

          {/* Right: text */}
          <div>
            <div className="reliability-label">Reliability</div>
            <h2 id="reliability-heading" className="reliability-heading">
              Designed to keep working when connectivity cannot be guaranteed.
            </h2>
            <p className="reliability-body">
              LiftCare is built around the practical reality of building environments.
              Network access is not always reliable. The system stores and processes
              all critical data locally, so nothing is lost during an outage.
              When connectivity returns, everything synchronizes automatically.
            </p>
            {!isAuthenticated && (
              <Link to="/login" className="btn btn-outline-white">
                Access Building Portal
              </Link>
            )}
          </div>
        </div>
      </section>

      <hr className="pub-divider" />

      {/* ──────────────────────────────────────────────────────
          ABOUT SECTION
      ────────────────────────────────────────────────────── */}
      <section id="about" aria-labelledby="about-heading">
        <div className="pub-section pub-section-sm">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4rem', alignItems: 'center' }}>
            <div>
              <div className="pub-section-label">About LiftCare</div>
              <h2 id="about-heading" className="pub-section-heading">
                Purpose-built for building-level elevator management.
              </h2>
              <p className="pub-section-body" style={{ marginTop: '1rem' }}>
                LiftCare is an offline-first elevator issue management and automatic escalation system.
                It is designed for deployment within a building's local area network, serving residents,
                building staff, and management teams through a single coordinated platform.
              </p>
              <p className="pub-section-body" style={{ marginTop: '1rem' }}>
                The system manages fault records in software. It does not connect directly to elevator
                hardware, sensors, or emergency dispatch services.
              </p>
            </div>

            {/* Feature summary cards */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
              {[
                { label: 'Role-based access', desc: 'Six distinct roles — resident, security, technician, manager, supervisor, administrator.' },
                { label: 'Full audit trail', desc: 'Every status change, assignment, and note is recorded with actor and timestamp.' },
                { label: 'Offline queue', desc: 'Browser-side queue preserves actions during connectivity loss and replays them on reconnection.' },
                { label: 'SLA monitoring', desc: 'Configurable response and resolution deadlines with automatic tier escalation.' },
              ].map((f) => (
                <div key={f.label} style={{
                  display: 'flex',
                  gap: '1rem',
                  padding: '1rem 1.25rem',
                  background: '#ffffff',
                  border: '1px solid var(--border-light)',
                  borderRadius: 'var(--r-md)',
                }}>
                  <div style={{ paddingTop: '0.15rem', flexShrink: 0 }}>
                    <CheckCircleIcon size={18} color="var(--green)" />
                  </div>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: '0.875rem', color: 'var(--text-primary)' }}>{f.label}</div>
                    <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.15rem', lineHeight: 1.5 }}>{f.desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* ──────────────────────────────────────────────────────
          PORTAL CTA BAND
      ────────────────────────────────────────────────────── */}
      {!isAuthenticated && (
        <section style={{ background: 'var(--bg-surface-alt)', borderTop: '1px solid var(--border-light)', borderBottom: '1px solid var(--border-light)' }}>
          <div className="pub-section pub-section-sm" style={{ textAlign: 'center' }}>
            <h2 style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text-primary)', letterSpacing: '-0.025em', marginBottom: '0.75rem' }}>
              Ready to access your building portal?
            </h2>
            <p style={{ fontSize: '0.975rem', color: 'var(--text-secondary)', marginBottom: '1.75rem', maxWidth: '440px', margin: '0 auto 1.75rem' }}>
              Sign in to your building account to view incidents, submit reports, and monitor elevator status.
            </p>
            <div style={{ display: 'flex', gap: '0.85rem', justifyContent: 'center', flexWrap: 'wrap' }}>
              <Link to="/login" className="btn btn-primary btn-lg">
                Sign In to Portal
              </Link>
              <Link to="/register" className="btn btn-secondary btn-lg">
                Register as Resident
              </Link>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
