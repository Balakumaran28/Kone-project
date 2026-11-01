import React from 'react';

export default function ArchitectureDiagram() {
  return (
    <section id="architecture" className="section">
      <div className="section-header">
        <h2 className="section-title">System Architecture Overview</h2>
        <p className="section-subtitle">
          Offline-first multi-tier design optimized for building intranet isolation and guaranteed incident delivery.
        </p>
      </div>

      <div className="arch-container">
        <div className="arch-tier-flow">
          {/* Tier 1: Frontend */}
          <div className="arch-tier-card">
            <span className="arch-tier-badge badge-frontend">Tier 1: Client</span>
            <h3 className="arch-card-title">React + Vite UI</h3>
            <p className="arch-card-desc">
              Resident & staff portal running on mobile/desktop browsers over the local building Wi-Fi/LAN.
            </p>
            <div className="arch-card-tags">
              <span className="arch-tag">React 18</span>
              <span className="arch-tag">Vite</span>
              <span className="arch-tag">React Router</span>
              <span className="arch-tag">Responsive CSS</span>
            </div>
          </div>

          {/* Connection 1 */}
          <div className="arch-arrow">
            <span className="arch-arrow-line">──►</span>
            <span>REST / JSON</span>
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Local LAN</span>
          </div>

          {/* Tier 2: Local Server */}
          <div className="arch-tier-card">
            <span className="arch-tier-badge badge-backend">Tier 2: Building Node</span>
            <h3 className="arch-card-title">Local Spring Boot</h3>
            <p className="arch-card-desc">
              On-premise appliance processing elevator incidents, SLA escalation, and alerts without external internet.
            </p>
            <div className="arch-card-tags">
              <span className="arch-tag">Java 21/25</span>
              <span className="arch-tag">Spring Boot 3</span>
              <span className="arch-tag">Spring Security</span>
              <span className="arch-tag">REST API</span>
            </div>
          </div>

          {/* Connection 2 */}
          <div className="arch-arrow">
            <span className="arch-arrow-line">──►</span>
            <span>JPA / JDBC</span>
            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)' }}>Port 5432</span>
          </div>

          {/* Tier 3: Local Database */}
          <div className="arch-tier-card">
            <span className="arch-tier-badge badge-db">Tier 3: Persistence</span>
            <h3 className="arch-card-title">Local PostgreSQL</h3>
            <p className="arch-card-desc">
              Durable relational store for buildings, elevators, incidents, audit trails, and offline sync queues.
            </p>
            <div className="arch-card-tags">
              <span className="arch-tag">PostgreSQL</span>
              <span className="arch-tag">Spring Data JPA</span>
              <span className="arch-tag">Transactions</span>
              <span className="arch-tag">Outbox Queue</span>
            </div>
          </div>
        </div>

        {/* Future sync callout */}
        <div className="arch-note-box">
          <strong>Offline-First Resilience Principle:</strong> The local building server operates 100% autonomously during internet outages. All reported elevator failures are recorded locally, timestamps are verified, and technicians are escalated on-site. When cloud connectivity is restored, an asynchronous synchronization protocol securely replicates incident records to the central cloud coordination server.
        </div>
      </div>
    </section>
  );
}
