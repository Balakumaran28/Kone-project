import React, { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { fetchBuildings, createBuilding } from '../services/api';

export default function BuildingsPage() {
  const { user, token } = useAuth();

  const [buildings, setBuildings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [includeInactive, setIncludeInactive] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  // Modal / Form state for creating building
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState('');
  const [formData, setFormData] = useState({
    buildingCode: '',
    name: '',
    address: '',
    city: '',
    localNodeId: '',
  });

  const canCreate = user?.role === 'ADMINISTRATOR';

  const loadBuildings = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      const data = await fetchBuildings(token, includeInactive);
      setBuildings(data);
    } catch (err) {
      setError(err.message || 'Unable to load buildings from local server.');
    } finally {
      setLoading(false);
    }
  }, [token, includeInactive]);

  useEffect(() => {
    loadBuildings();
  }, [loadBuildings]);

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setFormError('');

    if (!formData.buildingCode.trim() || !formData.name.trim() || !formData.address.trim()) {
      setFormError('Building code, name, and address are required.');
      return;
    }

    setCreating(true);
    try {
      await createBuilding(token, {
        buildingCode: formData.buildingCode.trim(),
        name: formData.name.trim(),
        address: formData.address.trim(),
        city: formData.city.trim() || null,
        localNodeId: formData.localNodeId.trim() || null,
      });

      setSuccessMsg(`Building "${formData.name}" created successfully.`);
      setShowCreateModal(false);
      setFormData({ buildingCode: '', name: '', address: '', city: '', localNodeId: '' });
      await loadBuildings();
    } catch (err) {
      setFormError(err.message || 'Failed to create building.');
    } finally {
      setCreating(false);
    }
  };

  const filteredBuildings = buildings.filter((b) => {
    const term = searchTerm.toLowerCase();
    return (
      b.name.toLowerCase().includes(term) ||
      b.buildingCode.toLowerCase().includes(term) ||
      b.address.toLowerCase().includes(term) ||
      (b.city && b.city.toLowerCase().includes(term))
    );
  });

  return (
    <div>
      {/* Page Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>
            <span>Buildings & Facilities</span>
            <span className="badge badge-neutral" style={{ fontSize: '0.75rem' }}>
              {filteredBuildings.length} Properties
            </span>
          </h1>
          <p className="page-subtitle">
            Physical property complexes and elevator shafts managed by this local node
          </p>
        </div>
        <div className="page-actions">
          {canCreate && (
            <button
              onClick={() => {
                setShowCreateModal(true);
                setFormError('');
              }}
              className="btn btn-primary btn-sm"
            >
              <span>+</span> Add Building
            </button>
          )}
          <button onClick={loadBuildings} className="btn btn-secondary btn-sm" disabled={loading}>
            {loading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>
      </div>

      {successMsg && (
        <div
          style={{
            padding: '0.75rem 1rem',
            background: 'var(--success-bg)',
            border: '1px solid var(--success-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--success-text)',
            fontSize: '0.85rem',
            marginBottom: '1rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>✓ {successMsg}</span>
          <button
            onClick={() => setSuccessMsg('')}
            style={{ background: 'transparent', border: 'none', color: 'inherit', cursor: 'pointer' }}
          >
            ✕
          </button>
        </div>
      )}

      {error && (
        <div
          style={{
            padding: '0.75rem 1rem',
            background: 'var(--danger-bg)',
            border: '1px solid var(--danger-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--danger-text)',
            fontSize: '0.85rem',
            marginBottom: '1rem',
          }}
        >
          ⚠️ {error}
        </div>
      )}

      {/* Filter Toolbar */}
      <div className="filter-bar">
        <div className="filter-group" style={{ flex: '1 1 240px' }}>
          <input
            type="text"
            className="form-input"
            placeholder="Search by building name, code, or address..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            style={{ fontSize: '0.82rem', padding: '0.45rem 0.75rem' }}
          />
        </div>

        <div className="filter-group">
          <label
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              fontSize: '0.82rem',
              color: 'var(--text-secondary)',
              cursor: 'pointer',
              userSelect: 'none',
            }}
          >
            <input
              type="checkbox"
              checked={includeInactive}
              onChange={(e) => setIncludeInactive(e.target.checked)}
              style={{ cursor: 'pointer' }}
            />
            Show Inactive Properties
          </label>
        </div>
      </div>

      {/* Building Grid */}
      {loading ? (
        <div className="card" style={{ textAlign: 'center', padding: '3rem' }}>
          <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
            Loading building registry from local database...
          </div>
        </div>
      ) : filteredBuildings.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🏢</div>
          <h3 className="empty-title">No Buildings Found</h3>
          <p className="empty-desc">
            {searchTerm
              ? 'No property complexes matched your search.'
              : 'No buildings have been provisioned on this local node yet.'}
          </p>
          {canCreate && (
            <button
              onClick={() => setShowCreateModal(true)}
              className="btn btn-primary btn-sm"
            >
              Add First Building
            </button>
          )}
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.25rem' }}>
          {filteredBuildings.map((b) => (
            <div
              key={b.id}
              className="card"
              style={{
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                transition: 'border-color 0.15s ease',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '0.75rem', marginBottom: '0.75rem' }}>
                  <div>
                    <span
                      className="badge badge-info"
                      style={{ fontFamily: 'var(--font-mono)', fontSize: '0.72rem', marginBottom: '0.35rem' }}
                    >
                      {b.buildingCode}
                    </span>
                    <h3 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--text-primary)', margin: 0 }}>
                      {b.name}
                    </h3>
                  </div>
                  <span
                    className={`badge ${b.active ? 'badge-success' : 'badge-neutral'}`}
                  >
                    {b.active ? 'ACTIVE' : 'INACTIVE'}
                  </span>
                </div>

                <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginBottom: '0.85rem', lineHeight: 1.4 }}>
                  📍 {b.address}{b.city ? `, ${b.city}` : ''}
                </p>

                <div
                  style={{
                    background: 'var(--bg-secondary)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '0.65rem 0.85rem',
                    fontSize: '0.78rem',
                    color: 'var(--text-muted)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <span>Node: <code>{b.localNodeId || 'building-local-node-01'}</code></span>
                  <span style={{ color: 'var(--elevator-blue-primary)', fontWeight: 600 }}>
                    {b.elevators?.length || 0} Elevator Shafts
                  </span>
                </div>
              </div>

              <div style={{ marginTop: '1.25rem', display: 'flex', gap: '0.5rem' }}>
                <Link
                  to={`/buildings/${b.id}`}
                  className="btn btn-primary btn-sm"
                  style={{ flex: 1 }}
                >
                  Inspect Shafts &rarr;
                </Link>
                <Link
                  to={`/incidents/report?buildingId=${b.id}`}
                  className="btn btn-secondary btn-sm"
                  title="Report incident for this building"
                >
                  + Ticket
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Building Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">Register New Building Complex</h2>
              <button
                className="modal-close"
                onClick={() => setShowCreateModal(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateSubmit}>
              <div className="modal-body">
                {formError && (
                  <div
                    style={{
                      padding: '0.65rem 0.85rem',
                      background: 'var(--danger-bg)',
                      border: '1px solid var(--danger-border)',
                      borderRadius: 'var(--radius-sm)',
                      color: 'var(--danger-text)',
                      fontSize: '0.8rem',
                      marginBottom: '1rem',
                    }}
                  >
                    ⚠️ {formError}
                  </div>
                )}

                <div className="form-group">
                  <label className="form-label" htmlFor="buildingCode">
                    Building Code <span className="required">*</span>
                  </label>
                  <input
                    id="buildingCode"
                    type="text"
                    required
                    className="form-input"
                    value={formData.buildingCode}
                    onChange={(e) => setFormData({ ...formData, buildingCode: e.target.value.toUpperCase() })}
                    placeholder="e.g. BLD-GAMMA"
                  />
                  <span className="form-hint">Unique alphanumeric identifier</span>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="buildingName">
                    Building Name <span className="required">*</span>
                  </label>
                  <input
                    id="buildingName"
                    type="text"
                    required
                    className="form-input"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    placeholder="e.g. Horizon Plaza South"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="address">
                    Physical Address <span className="required">*</span>
                  </label>
                  <input
                    id="address"
                    type="text"
                    required
                    className="form-input"
                    value={formData.address}
                    onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                    placeholder="e.g. 104 Grand Avenue"
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.85rem' }}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="city">
                      City
                    </label>
                    <input
                      id="city"
                      type="text"
                      className="form-input"
                      value={formData.city}
                      onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                      placeholder="Metro City"
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="localNodeId">
                      Local Node ID
                    </label>
                    <input
                      id="localNodeId"
                      type="text"
                      className="form-input"
                      value={formData.localNodeId}
                      onChange={(e) => setFormData({ ...formData, localNodeId: e.target.value })}
                      placeholder="building-local-node-01"
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => setShowCreateModal(false)}
                  disabled={creating}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary btn-sm"
                  disabled={creating}
                >
                  {creating ? 'Saving Property...' : 'Save Building'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
