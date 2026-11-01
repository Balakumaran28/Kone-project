import React, { useState, useEffect, useCallback } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  fetchBuildingById,
  updateBuilding,
  deactivateBuilding,
  fetchElevatorsByBuilding,
  createElevator,
  updateElevator,
  updateElevatorStatus,
  deactivateElevator,
} from '../services/api';

export default function BuildingDetailPage() {
  const { id } = useParams();
  const { user, token } = useAuth();
  const navigate = useNavigate();

  const [building, setBuilding] = useState(null);
  const [elevators, setElevators] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notification, setNotification] = useState('');

  // Modals
  const [showEditBuildingModal, setShowEditBuildingModal] = useState(false);
  const [editBuildingData, setEditBuildingData] = useState({ name: '', address: '', city: '', localNodeId: '', active: true });
  const [savingBuilding, setSavingBuilding] = useState(false);

  const [showElevatorModal, setShowElevatorModal] = useState(false);
  const [editingElevatorId, setEditingElevatorId] = useState(null);
  const [elevatorFormData, setElevatorFormData] = useState({
    elevatorCode: '',
    locationDescription: '',
    manufacturer: '',
    model: '',
    serviceProvider: '',
    totalFloors: '',
    operationalStatus: 'ACTIVE',
  });
  const [savingElevator, setSavingElevator] = useState(false);
  const [elevatorModalError, setElevatorModalError] = useState('');

  // Status updating indicator per elevator
  const [updatingStatusId, setUpdatingStatusId] = useState(null);

  // Role permissions
  const canEditBuilding = ['ADMINISTRATOR', 'BUILDING_MANAGER'].includes(user?.role);
  const canDeactivateBuilding = user?.role === 'ADMINISTRATOR';
  const canManageElevators = ['ADMINISTRATOR', 'BUILDING_MANAGER'].includes(user?.role);
  const canUpdateStatus = ['ADMINISTRATOR', 'BUILDING_MANAGER', 'TECHNICIAN'].includes(user?.role);

  const loadData = useCallback(async () => {
    if (!token || !id) return;
    setLoading(true);
    setError('');
    try {
      const [buildingData, elevatorList] = await Promise.all([
        fetchBuildingById(token, id),
        fetchElevatorsByBuilding(token, id),
      ]);
      setBuilding(buildingData);
      setElevators(elevatorList);
      setEditBuildingData({
        name: buildingData.name,
        address: buildingData.address,
        city: buildingData.city || '',
        localNodeId: buildingData.localNodeId || '',
        active: buildingData.active,
      });
    } catch (err) {
      setError(err.message || 'Failed to load building and elevator details.');
    } finally {
      setLoading(false);
    }
  }, [token, id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const showNotification = (msg) => {
    setNotification(msg);
    setTimeout(() => setNotification(''), 4000);
  };

  const handleUpdateBuilding = async (e) => {
    e.preventDefault();
    setSavingBuilding(true);
    try {
      const updated = await updateBuilding(token, id, {
        name: editBuildingData.name.trim(),
        address: editBuildingData.address.trim(),
        city: editBuildingData.city.trim() || null,
        localNodeId: editBuildingData.localNodeId.trim() || null,
        active: editBuildingData.active,
      });
      setBuilding(updated);
      setShowEditBuildingModal(false);
      showNotification('Building details updated successfully.');
    } catch (err) {
      setError(err.message || 'Failed to update building details.');
    } finally {
      setSavingBuilding(false);
    }
  };

  const handleDeactivateBuilding = async () => {
    if (!window.confirm(`Are you sure you want to deactivate "${building.name}"? Historical elevator records will be preserved.`)) {
      return;
    }
    try {
      const updated = await deactivateBuilding(token, id);
      setBuilding(updated);
      showNotification('Building has been marked as inactive.');
    } catch (err) {
      setError(err.message || 'Failed to deactivate building.');
    }
  };

  const openElevatorModal = (elevatorToEdit = null) => {
    setElevatorModalError('');
    if (elevatorToEdit) {
      setEditingElevatorId(elevatorToEdit.id);
      setElevatorFormData({
        elevatorCode: elevatorToEdit.elevatorCode,
        locationDescription: elevatorToEdit.locationDescription || '',
        manufacturer: elevatorToEdit.manufacturer || '',
        model: elevatorToEdit.model || '',
        serviceProvider: elevatorToEdit.serviceProvider || '',
        totalFloors: elevatorToEdit.totalFloors ? String(elevatorToEdit.totalFloors) : '',
        operationalStatus: elevatorToEdit.operationalStatus || 'ACTIVE',
      });
    } else {
      setEditingElevatorId(null);
      setElevatorFormData({
        elevatorCode: '',
        locationDescription: '',
        manufacturer: '',
        model: '',
        serviceProvider: '',
        totalFloors: '',
        operationalStatus: 'ACTIVE',
      });
    }
    setShowElevatorModal(true);
  };

  const handleSaveElevator = async (e) => {
    e.preventDefault();
    setElevatorModalError('');

    if (!elevatorFormData.elevatorCode.trim()) {
      setElevatorModalError('Elevator code is required.');
      return;
    }

    const payload = {
      elevatorCode: elevatorFormData.elevatorCode.trim(),
      locationDescription: elevatorFormData.locationDescription.trim() || null,
      manufacturer: elevatorFormData.manufacturer.trim() || null,
      model: elevatorFormData.model.trim() || null,
      serviceProvider: elevatorFormData.serviceProvider.trim() || null,
      totalFloors: elevatorFormData.totalFloors ? parseInt(elevatorFormData.totalFloors, 10) : null,
      operationalStatus: elevatorFormData.operationalStatus,
    };

    setSavingElevator(true);
    try {
      if (editingElevatorId) {
        await updateElevator(token, editingElevatorId, payload);
        showNotification(`Elevator ${payload.elevatorCode} updated successfully.`);
      } else {
        await createElevator(token, id, payload);
        showNotification(`Elevator ${payload.elevatorCode} registered under building.`);
      }
      setShowElevatorModal(false);
      await loadData();
    } catch (err) {
      setElevatorModalError(err.message || 'Failed to save elevator.');
    } finally {
      setSavingElevator(false);
    }
  };

  const handleStatusChange = async (elevatorId, newStatus) => {
    setUpdatingStatusId(elevatorId);
    try {
      await updateElevatorStatus(token, elevatorId, newStatus);
      setElevators((prev) =>
        prev.map((el) => (el.id === elevatorId ? { ...el, operationalStatus: newStatus } : el))
      );
      showNotification(`Elevator operational status updated to ${newStatus}.`);
    } catch (err) {
      setError(err.message || 'Failed to update status.');
    } finally {
      setUpdatingStatusId(null);
    }
  };

  const handleDeactivateElevator = async (elevatorId, code) => {
    if (!window.confirm(`Set elevator "${code}" to OUT_OF_SERVICE? Historical data will be preserved.`)) {
      return;
    }
    try {
      await deactivateElevator(token, elevatorId);
      setElevators((prev) =>
        prev.map((el) => (el.id === elevatorId ? { ...el, operationalStatus: 'OUT_OF_SERVICE' } : el))
      );
      showNotification(`Elevator "${code}" has been set to OUT_OF_SERVICE.`);
    } catch (err) {
      setError(err.message || 'Failed to deactivate elevator.');
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'ACTIVE':
        return <span className="badge badge-success">Active</span>;
      case 'MAINTENANCE':
        return <span className="badge badge-warning">Maintenance</span>;
      case 'OUT_OF_SERVICE':
      default:
        return <span className="badge badge-critical">Out of Service</span>;
    }
  };

  if (loading && !building) {
    return (
      <div className="card" style={{ textAlign: 'center', padding: '3.5rem' }}>
        <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Loading building and elevator shaft telemetry...
        </div>
      </div>
    );
  }

  if (error && !building) {
    return (
      <div style={{ maxWidth: '800px', margin: '2rem auto' }}>
        <div
          style={{
            padding: '1rem',
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
        <Link to="/buildings" className="btn btn-secondary btn-sm">
          &larr; Back to Buildings List
        </Link>
      </div>
    );
  }

  return (
    <div>
      {/* Navigation Breadcrumb */}
      <div style={{ marginBottom: '1rem' }}>
        <Link to="/buildings" style={{ color: 'var(--accent-cyan)', fontSize: '0.82rem', fontWeight: 600 }}>
          &larr; Back to Buildings List
        </Link>
      </div>

      {notification && (
        <div
          style={{
            padding: '0.75rem 1rem',
            background: 'var(--success-bg)',
            border: '1px solid var(--success-border)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--success-text)',
            fontSize: '0.85rem',
            marginBottom: '1.25rem',
          }}
        >
          ✓ {notification}
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
            marginBottom: '1.25rem',
          }}
        >
          ⚠️ {error}
        </div>
      )}

      {/* Building Header Card */}
      {building && (
        <div className="card" style={{ marginBottom: '1.5rem', padding: '1.5rem' }}>
          <div className="page-header" style={{ marginBottom: '1.25rem' }}>
            <div className="page-title-group">
              <h1>
                <span>{building.name}</span>
                <span className="badge badge-info" style={{ fontFamily: 'var(--font-mono)' }}>
                  {building.buildingCode}
                </span>
                <span className={`badge ${building.active ? 'badge-success' : 'badge-critical'}`}>
                  {building.active ? 'Active Property' : 'Deactivated'}
                </span>
              </h1>
              <p className="page-subtitle">
                📍 {building.address} {building.city ? `• ${building.city}` : ''}
              </p>
            </div>

            <div className="page-actions">
              {canEditBuilding && (
                <button onClick={() => setShowEditBuildingModal(true)} className="btn btn-secondary btn-sm">
                  ✏️ Edit Details
                </button>
              )}
              {canDeactivateBuilding && building.active && (
                <button onClick={handleDeactivateBuilding} className="btn btn-danger btn-sm">
                  Deactivate Property
                </button>
              )}
              <Link to={`/incidents/report?buildingId=${building.id}`} className="btn btn-warning btn-sm">
                + Report Issue
              </Link>
            </div>
          </div>

          <div className="metric-grid" style={{ marginBottom: 0 }}>
            <div className="metric-card">
              <span className="metric-label">Local Server Node</span>
              <div className="metric-value" style={{ fontSize: '1rem', fontFamily: 'var(--font-mono)' }}>
                {building.localNodeId || 'Default Node'}
              </div>
              <div className="metric-subtext">On-premise hardware host</div>
            </div>

            <div className="metric-card">
              <span className="metric-label">Total Shafts</span>
              <div className="metric-value" style={{ fontSize: '1.4rem', color: 'var(--elevator-blue-primary)' }}>
                {elevators.length}
              </div>
              <div className="metric-subtext">Configured car shafts</div>
            </div>

            <div className="metric-card success">
              <span className="metric-label">Operational Cars</span>
              <div className="metric-value" style={{ fontSize: '1.4rem', color: '#15803d' }}>
                {elevators.filter((e) => e.operationalStatus === 'ACTIVE').length}
              </div>
              <div className="metric-subtext">In active service</div>
            </div>

            <div className={`metric-card ${elevators.some((e) => e.operationalStatus !== 'ACTIVE') ? 'warning' : ''}`}>
              <span className="metric-label">Down / Maintenance</span>
              <div className="metric-value" style={{ fontSize: '1.4rem' }}>
                {elevators.filter((e) => e.operationalStatus !== 'ACTIVE').length}
              </div>
              <div className="metric-subtext">Restricted availability</div>
            </div>
          </div>
        </div>
      )}

      {/* Elevators List Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)', margin: 0 }}>
            Elevator Units in Building ({elevators.length})
          </h2>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', margin: '0.15rem 0 0 0' }}>
            Real-time operational status, shaft specs, and maintenance controls
          </p>
        </div>

        {canManageElevators && (
          <button onClick={() => openElevatorModal(null)} className="btn btn-primary btn-sm">
            <span>+</span> Add Elevator Shaft
          </button>
        )}
      </div>

      {/* Elevator Cards */}
      {elevators.length === 0 ? (
        <div className="empty-state">
          <div className="empty-icon">🛗</div>
          <h3 className="empty-title">No Elevators Configured</h3>
          <p className="empty-desc">
            Add elevator units to enable fault reporting and SLA escalation monitoring for this complex.
          </p>
          {canManageElevators && (
            <button onClick={() => openElevatorModal(null)} className="btn btn-primary btn-sm">
              Add First Elevator
            </button>
          )}
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {elevators.map((elevator) => (
            <div
              key={elevator.id}
              className="card"
              style={{
                borderLeft:
                  elevator.operationalStatus === 'ACTIVE'
                    ? '4px solid #10b981'
                    : elevator.operationalStatus === 'MAINTENANCE'
                    ? '4px solid #f59e0b'
                    : '4px solid #ef4444',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '0.75rem', marginBottom: '0.85rem' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                    <span style={{ fontSize: '1.15rem', fontWeight: 800, color: 'var(--text-primary)' }}>
                      {elevator.elevatorCode}
                    </span>
                    {getStatusBadge(elevator.operationalStatus)}
                    {updatingStatusId === elevator.id && (
                      <span style={{ fontSize: '0.75rem', color: 'var(--elevator-blue-primary)' }}>Updating...</span>
                    )}
                  </div>
                  <div style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', marginTop: '0.2rem' }}>
                    {elevator.locationDescription || 'Location description not specified'}
                  </div>
                </div>

                <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                  <Link
                    to={`/incidents/report?buildingId=${id}&elevatorId=${elevator.id}`}
                    className="btn btn-secondary btn-sm"
                    style={{ fontSize: '0.75rem' }}
                  >
                    + Report Fault
                  </Link>
                  {canManageElevators && (
                    <button
                      onClick={() => openElevatorModal(elevator)}
                      className="btn btn-secondary btn-sm"
                      style={{ fontSize: '0.75rem' }}
                    >
                      ✏️ Edit
                    </button>
                  )}
                  {canManageElevators && elevator.operationalStatus !== 'OUT_OF_SERVICE' && (
                    <button
                      onClick={() => handleDeactivateElevator(elevator.id, elevator.elevatorCode)}
                      className="btn btn-danger btn-sm"
                      style={{ fontSize: '0.75rem' }}
                    >
                      Decommission
                    </button>
                  )}
                </div>
              </div>

              {/* Specs Grid */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
                  gap: '0.75rem',
                  padding: '0.75rem 1rem',
                  background: 'var(--bg-secondary)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.8rem',
                  marginBottom: '0.85rem',
                }}
              >
                <div>
                  <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.72rem' }}>Manufacturer</span>
                  <strong style={{ color: 'var(--text-primary)' }}>{elevator.manufacturer || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.72rem' }}>Model</span>
                  <strong style={{ color: 'var(--text-primary)' }}>{elevator.model || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.72rem' }}>Service Provider</span>
                  <strong style={{ color: 'var(--text-primary)' }}>{elevator.serviceProvider || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted)', display: 'block', fontSize: '0.72rem' }}>Floors Served</span>
                  <strong style={{ color: 'var(--text-primary)' }}>{elevator.totalFloors ? `${elevator.totalFloors} Floors` : '—'}</strong>
                </div>
              </div>

              {/* Operational Status Control Bar */}
              {canUpdateStatus && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: '0.6rem',
                    paddingTop: '0.65rem',
                    borderTop: '1px solid var(--border-subtle)',
                  }}
                >
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', fontWeight: 600 }}>
                    Operational Status Override:
                  </span>
                  <div style={{ display: 'flex', gap: '0.4rem' }}>
                    <button
                      onClick={() => handleStatusChange(elevator.id, 'ACTIVE')}
                      disabled={updatingStatusId === elevator.id || elevator.operationalStatus === 'ACTIVE'}
                      className="btn btn-sm"
                      style={{
                        background: elevator.operationalStatus === 'ACTIVE' ? 'var(--success-bg)' : 'transparent',
                        borderColor: elevator.operationalStatus === 'ACTIVE' ? 'var(--success-border)' : 'var(--border-subtle)',
                        color: elevator.operationalStatus === 'ACTIVE' ? 'var(--success-text)' : 'var(--text-muted)',
                        fontSize: '0.74rem',
                      }}
                    >
                      ● ACTIVE
                    </button>
                    <button
                      onClick={() => handleStatusChange(elevator.id, 'MAINTENANCE')}
                      disabled={updatingStatusId === elevator.id || elevator.operationalStatus === 'MAINTENANCE'}
                      className="btn btn-sm"
                      style={{
                        background: elevator.operationalStatus === 'MAINTENANCE' ? 'var(--warning-bg)' : 'transparent',
                        borderColor: elevator.operationalStatus === 'MAINTENANCE' ? 'var(--warning-border)' : 'var(--border-subtle)',
                        color: elevator.operationalStatus === 'MAINTENANCE' ? 'var(--warning-text)' : 'var(--text-muted)',
                        fontSize: '0.74rem',
                      }}
                    >
                      ▲ MAINTENANCE
                    </button>
                    <button
                      onClick={() => handleStatusChange(elevator.id, 'OUT_OF_SERVICE')}
                      disabled={updatingStatusId === elevator.id || elevator.operationalStatus === 'OUT_OF_SERVICE'}
                      className="btn btn-sm"
                      style={{
                        background: elevator.operationalStatus === 'OUT_OF_SERVICE' ? 'var(--danger-bg)' : 'transparent',
                        borderColor: elevator.operationalStatus === 'OUT_OF_SERVICE' ? 'var(--danger-border)' : 'var(--border-subtle)',
                        color: elevator.operationalStatus === 'OUT_OF_SERVICE' ? 'var(--danger-text)' : 'var(--text-muted)',
                        fontSize: '0.74rem',
                      }}
                    >
                      ■ OUT OF SERVICE
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Edit Building Modal */}
      {showEditBuildingModal && (
        <div className="modal-overlay" onClick={() => setShowEditBuildingModal(false)}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">Edit Building Details</h2>
              <button className="modal-close" onClick={() => setShowEditBuildingModal(false)}>✕</button>
            </div>
            <form onSubmit={handleUpdateBuilding}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label" htmlFor="editName">Building Name <span className="required">*</span></label>
                  <input
                    id="editName"
                    type="text"
                    required
                    className="form-input"
                    value={editBuildingData.name}
                    onChange={(e) => setEditBuildingData({ ...editBuildingData, name: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="editAddress">Physical Address <span className="required">*</span></label>
                  <input
                    id="editAddress"
                    type="text"
                    required
                    className="form-input"
                    value={editBuildingData.address}
                    onChange={(e) => setEditBuildingData({ ...editBuildingData, address: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="editCity">City</label>
                  <input
                    id="editCity"
                    type="text"
                    className="form-input"
                    value={editBuildingData.city}
                    onChange={(e) => setEditBuildingData({ ...editBuildingData, city: e.target.value })}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="editNode">Local Node ID</label>
                  <input
                    id="editNode"
                    type="text"
                    className="form-input"
                    value={editBuildingData.localNodeId}
                    onChange={(e) => setEditBuildingData({ ...editBuildingData, localNodeId: e.target.value })}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowEditBuildingModal(false)} disabled={savingBuilding}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary btn-sm" disabled={savingBuilding}>
                  {savingBuilding ? 'Saving Changes...' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add / Edit Elevator Modal */}
      {showElevatorModal && (
        <div className="modal-overlay" onClick={() => setShowElevatorModal(false)}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-title">
                {editingElevatorId ? 'Edit Elevator Unit' : 'Register Elevator Shaft'}
              </h2>
              <button className="modal-close" onClick={() => setShowElevatorModal(false)}>✕</button>
            </div>
            <form onSubmit={handleSaveElevator}>
              <div className="modal-body">
                {elevatorModalError && (
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
                    ⚠️ {elevatorModalError}
                  </div>
                )}
                <div className="form-group">
                  <label className="form-label" htmlFor="elvCode">Elevator Code <span className="required">*</span></label>
                  <input
                    id="elvCode"
                    type="text"
                    required
                    className="form-input"
                    value={elevatorFormData.elevatorCode}
                    onChange={(e) => setElevatorFormData({ ...elevatorFormData, elevatorCode: e.target.value.toUpperCase() })}
                    placeholder="e.g. LIFT-01"
                  />
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="elvLoc">Location Description</label>
                  <input
                    id="elvLoc"
                    type="text"
                    className="form-input"
                    value={elevatorFormData.locationDescription}
                    onChange={(e) => setElevatorFormData({ ...elevatorFormData, locationDescription: e.target.value })}
                    placeholder="e.g. North Tower Lobby - Shaft A"
                  />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.85rem' }}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="elvMfr">Manufacturer</label>
                    <input
                      id="elvMfr"
                      type="text"
                      className="form-input"
                      value={elevatorFormData.manufacturer}
                      onChange={(e) => setElevatorFormData({ ...elevatorFormData, manufacturer: e.target.value })}
                      placeholder="e.g. KONE, Otis"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="elvModel">Model</label>
                    <input
                      id="elvModel"
                      type="text"
                      className="form-input"
                      value={elevatorFormData.model}
                      onChange={(e) => setElevatorFormData({ ...elevatorFormData, model: e.target.value })}
                      placeholder="MonoSpace 500"
                    />
                  </div>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.85rem' }}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="elvFloors">Floors Served</label>
                    <input
                      id="elvFloors"
                      type="number"
                      min="1"
                      className="form-input"
                      value={elevatorFormData.totalFloors}
                      onChange={(e) => setElevatorFormData({ ...elevatorFormData, totalFloors: e.target.value })}
                      placeholder="18"
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="elvStatus">Initial Status</label>
                    <select
                      id="elvStatus"
                      className="form-select"
                      value={elevatorFormData.operationalStatus}
                      onChange={(e) => setElevatorFormData({ ...elevatorFormData, operationalStatus: e.target.value })}
                    >
                      <option value="ACTIVE">Active</option>
                      <option value="MAINTENANCE">Maintenance</option>
                      <option value="OUT_OF_SERVICE">Out of Service</option>
                    </select>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setShowElevatorModal(false)} disabled={savingElevator}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary btn-sm" disabled={savingElevator}>
                  {savingElevator ? 'Saving Shaft...' : 'Save Elevator'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
