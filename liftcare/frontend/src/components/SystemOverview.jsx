import React from 'react';

const MODULES = [
  {
    id: 'MOD-01',
    title: 'Project Setup & Architecture',
    desc: 'React + Vite frontend, Spring Boot 3 backend foundation, health probe, CORS, and unified error model.',
    status: 'COMPLETE',
  },
  {
    id: 'MOD-02',
    title: 'Database Design & Entities',
    desc: 'PostgreSQL schema for Users, Buildings, Elevators, Issue Categories, Incidents, and Audit history.',
    status: 'COMPLETE',
  },
  {
    id: 'MOD-03',
    title: 'Authentication & Access Control',
    desc: 'Local JWT authentication, BCrypt password hashing, stateless security filters, and role-based access control.',
    status: 'COMPLETE',
  },
  {
    id: 'MOD-04',
    title: 'Building & Elevator Management',
    desc: 'Property complex management, elevator shaft registry, scoped uniqueness validation, and operational status controls.',
    status: 'COMPLETE',
  },
  {
    id: 'MOD-05',
    title: 'Incident Reporting & Local Triage',
    desc: 'Fault reporting, passenger entrapment priority handling, automatic elevator safety shutdowns, technician assignment, and immutable audit trails.',
    status: 'COMPLETE',
  },
  {
    id: 'MOD-06',
    title: 'Building Control-Room Dashboard',
    desc: 'Real-time telemetry summary, entrapment emergency banner, operational fleet safety matrix, and prioritized incident queue.',
    status: 'COMPLETE',
  },
  {
    id: 'MOD-07',
    title: 'SLA Engine & Auto-Escalation',
    desc: 'Real-time countdown timer, tier-1/2/3 automatic technician escalation rules, and breach alerts.',
    status: 'UPCOMING',
  },
];

export default function SystemOverview() {
  return (
    <section id="roadmap" className="section">
      <div className="section-header">
        <h2 className="section-title">Development Roadmap & Integration</h2>
        <p className="section-subtitle">
          Incremental engineering modules built upon this robust, decoupled foundation.
        </p>
      </div>

      <div className="features-grid">
        {MODULES.map((mod) => (
          <div key={mod.id} className="feature-card">
            <div className="feature-header">
              <span className="feature-title">{mod.title}</span>
              <span
                className={`module-badge ${
                  mod.status === 'COMPLETE' ? 'active' : mod.status === 'ACTIVE' ? 'active' : 'upcoming'
                }`}
              >
                {mod.status === 'COMPLETE' ? '✓ Completed' : mod.status === 'ACTIVE' ? '⚡ Module 3 Active' : mod.id}
              </span>
            </div>
            <p className="feature-desc">{mod.desc}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
