import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import {
  fetchBuildings,
  fetchElevatorsByBuilding,
  fetchIssueCategories,
  createIncident,
} from '../services/api';
import { enqueueAction } from '../services/offlineQueue';

export default function ReportIncidentPage() {
  const { token, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const queryParams = new URLSearchParams(location.search);
  const prefilledBuildingId = queryParams.get('buildingId') || '';
  const prefilledElevatorId = queryParams.get('elevatorId') || '';

  const [buildings, setBuildings] = useState([]);
  const [elevators, setElevators] = useState([]);
  const [categories, setCategories] = useState([]);

  const [buildingId, setBuildingId] = useState(prefilledBuildingId);
  const [elevatorId, setElevatorId] = useState(prefilledElevatorId);
  const [issueCategoryId, setIssueCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [severity, setSeverity] = useState('MEDIUM');
  const [entrapmentReported, setEntrapmentReported] = useState(false);

  const [loadingOptions, setLoadingOptions] = useState(true);
  const [loadingElevators, setLoadingElevators] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [queuedNotice, setQueuedNotice] = useState(false);

  useEffect(() => {
    async function loadInitialData() {
      if (!token) return;
      setLoadingOptions(true);
      setError('');
      try {
        const [buildingList, categoryList] = await Promise.all([
          fetchBuildings(token, false),
          fetchIssueCategories(token),
        ]);
        setBuildings(buildingList);
        setCategories(categoryList);

        if (prefilledBuildingId) {
          loadElevators(prefilledBuildingId);
        }
      } catch (err) {
        setError(err.message || 'Failed to load building and category data.');
      } finally {
        setLoadingOptions(false);
      }
    }

    loadInitialData();
  }, [token, prefilledBuildingId]);

  const loadElevators = async (bId) => {
    if (!bId) {
      setElevators([]);
      return;
    }
    setLoadingElevators(true);
    try {
      const elevatorList = await fetchElevatorsByBuilding(token, bId);
      setElevators(elevatorList);
    } catch (err) {
      setError(err.message || 'Failed to load elevators for selected building.');
    } finally {
      setLoadingElevators(false);
    }
  };

  const handleBuildingChange = (e) => {
    const newBldgId = e.target.value;
    setBuildingId(newBldgId);
    setElevatorId('');
    if (newBldgId) {
      loadElevators(newBldgId);
    } else {
      setElevators([]);
    }
  };

  const handleCategoryChange = (e) => {
    const catId = e.target.value;
    setIssueCategoryId(catId);
    if (!catId) return;

    const selectedCat = categories.find((c) => String(c.id) === String(catId));
    if (selectedCat) {
      if (selectedCat.code === 'PASSENGER_ENTRAPMENT' || selectedCat.requiresImmediateAttention) {
        setEntrapmentReported(true);
        setSeverity('CRITICAL');
      } else {
        setSeverity(selectedCat.defaultSeverity || 'MEDIUM');
      }
    }
  };

  const handleEntrapmentToggle = (e) => {
    const isChecked = e.target.checked;
    setEntrapmentReported(isChecked);
    if (isChecked) {
      setSeverity('CRITICAL');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!buildingId) {
      setError('Please select a building complex.');
      return;
    }
    if (!elevatorId) {
      setError('Please select the elevator shaft unit.');
      return;
    }
    if (!issueCategoryId) {
      setError('Please select an issue category.');
      return;
    }
    if (!description.trim()) {
      setError('Please provide a descriptive explanation of the issue.');
      return;
    }

    const payload = {
      buildingId: Number(buildingId),
      elevatorId: Number(elevatorId),
      issueCategoryId: Number(issueCategoryId),
      description: description.trim(),
      severity,
      entrapmentReported,
    };

    setSubmitting(true);
    try {
      const created = await createIncident(token, payload);
      navigate(`/incidents/${created.id}`);
    } catch (err) {
      // If server or network was unreachable, enqueue to offline IndexedDB
      if (!navigator.onLine || err.message?.includes('NetworkError') || err.message?.includes('Failed to fetch') || err.status === 503) {
        try {
          await enqueueAction('CREATE_INCIDENT', payload, user?.email || null);
          setQueuedNotice(true);
          setTimeout(() => {
            navigate('/offline-queue');
          }, 2000);
          return;
        } catch (queueErr) {
          setError('Failed to submit incident and unable to buffer offline: ' + queueErr.message);
        }
      } else {
        setError(err.message || 'Failed to submit incident ticket.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '780px', margin: '1rem auto' }}>
      {/* Header */}
      <div className="page-header">
        <div className="page-title-group">
          <h1>
            <span>Report Elevator Fault</span>
          </h1>
          <p className="page-subtitle">
            Log mechanical defect or safety emergency into building local control desk
          </p>
        </div>
        <div className="page-actions">
          <Link to="/incidents" className="btn btn-secondary btn-sm">
            &larr; Back to Incidents
          </Link>
        </div>
      </div>

      {queuedNotice && (
        <div
          style={{
            padding: '1rem',
            background: 'var(--warning-bg)',
            border: '1px solid var(--warning-border)',
            borderRadius: 'var(--radius-md)',
            color: 'var(--warning-text)',
            fontSize: '0.88rem',
            marginBottom: '1.5rem',
          }}
        >
          <strong>📦 Offline Buffer Active:</strong> Local server could not be reached immediately. Your report has been securely saved to the browser's persistent queue and will sync automatically when connectivity resumes. Redirecting to queue...
        </div>
      )}

      {error && (
        <div
          style={{
            padding: '0.85rem 1rem',
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

      {/* Emergency Entrapment Callout Card */}
      <div
        style={{
          background: entrapmentReported ? '#fef2f2' : 'var(--bg-card)',
          border: entrapmentReported ? '1px solid #f87171' : '1px solid var(--border-default)',
          borderRadius: 'var(--radius-md)',
          padding: '1.25rem',
          marginBottom: '1.5rem',
          transition: 'all 0.2s ease',
        }}
      >
        <label
          style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: '0.85rem',
            cursor: 'pointer',
            userSelect: 'none',
          }}
        >
          <input
            type="checkbox"
            checked={entrapmentReported}
            onChange={handleEntrapmentToggle}
            style={{ width: '18px', height: '18px', marginTop: '0.2rem', accentColor: '#dc2626', cursor: 'pointer' }}
          />
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '1.1rem' }}>🚨</span>
              <strong style={{ fontSize: '0.98rem', color: entrapmentReported ? '#b91c1c' : 'var(--text-primary)' }}>
                Passenger Entrapment Emergency
              </strong>
              {entrapmentReported && (
                <span className="badge badge-critical">IMMEDIATE RESCUE PROTOCOL</span>
              )}
            </div>
            <p style={{ fontSize: '0.82rem', color: entrapmentReported ? '#991b1b' : 'var(--text-secondary)', marginTop: '0.25rem', lineHeight: 1.4 }}>
              Check this box if passengers are trapped inside an elevator car. This overrides severity to <strong>CRITICAL</strong>, immediately alerts on-duty security staff, and triggers expedited Tier 1 SLA response.
            </p>
          </div>
        </label>
      </div>

      {/* Main Intake Form */}
      <div className="card" style={{ padding: '1.75rem' }}>
        <form onSubmit={handleSubmit}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem', marginBottom: '1.25rem' }}>
            {/* Building Selection */}
            <div className="form-group" style={{ margin: 0 }}>
              <label className="form-label" htmlFor="buildingId">
                Building Complex <span className="required">*</span>
              </label>
              <select
                id="buildingId"
                className="form-select"
                value={buildingId}
                onChange={handleBuildingChange}
                disabled={loadingOptions}
                required
              >
                <option value="">-- Select Building --</option>
                {buildings.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} ({b.buildingCode})
                  </option>
                ))}
              </select>
              <span className="form-hint">Physical property managed by this node</span>
            </div>

            {/* Elevator Shaft Selection */}
            <div className="form-group" style={{ margin: 0 }}>
              <label className="form-label" htmlFor="elevatorId">
                Elevator Shaft Unit <span className="required">*</span>
              </label>
              <select
                id="elevatorId"
                className="form-select"
                value={elevatorId}
                onChange={(e) => setElevatorId(e.target.value)}
                disabled={!buildingId || loadingElevators}
                required
              >
                <option value="">
                  {loadingElevators
                    ? 'Loading elevators...'
                    : !buildingId
                    ? '-- Select building first --'
                    : '-- Select Elevator Shaft --'}
                </option>
                {elevators.map((elv) => (
                  <option key={elv.id} value={elv.id}>
                    {elv.elevatorCode} {elv.locationDescription ? `(${elv.locationDescription})` : ''} - [{elv.operationalStatus}]
                  </option>
                ))}
              </select>
              <span className="form-hint">Specific shaft car requiring diagnostic</span>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem', marginBottom: '1.25rem' }}>
            {/* Issue Category */}
            <div className="form-group" style={{ margin: 0 }}>
              <label className="form-label" htmlFor="issueCategoryId">
                Classification Category <span className="required">*</span>
              </label>
              <select
                id="issueCategoryId"
                className="form-select"
                value={issueCategoryId}
                onChange={handleCategoryChange}
                disabled={loadingOptions}
                required
              >
                <option value="">-- Select Classification --</option>
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name} ({cat.defaultSeverity})
                  </option>
                ))}
              </select>
              <span className="form-hint">Diagnostic classification taxonomy</span>
            </div>

            {/* Severity Level */}
            <div className="form-group" style={{ margin: 0 }}>
              <label className="form-label" htmlFor="severity">
                Assigned Severity Level <span className="required">*</span>
              </label>
              <select
                id="severity"
                className="form-select"
                value={severity}
                onChange={(e) => setSeverity(e.target.value)}
                disabled={entrapmentReported}
                required
              >
                <option value="LOW">LOW - Minor cosmetic or non-blocking defect</option>
                <option value="MEDIUM">MEDIUM - Standard operational issue / noise</option>
                <option value="HIGH">HIGH - Urgent shutdown / door malfunction</option>
                <option value="CRITICAL">CRITICAL - Entrapment or severe life-safety risk</option>
              </select>
              <span className="form-hint">
                {entrapmentReported ? 'Locked to CRITICAL due to active entrapment' : 'Affects SLA response deadlines'}
              </span>
            </div>
          </div>

          {/* Description */}
          <div className="form-group" style={{ marginBottom: '1.75rem' }}>
            <label className="form-label" htmlFor="description">
              Incident Details & Floor Location <span className="required">*</span>
            </label>
            <textarea
              id="description"
              className="form-textarea"
              rows={4}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Passenger car halted between floors 4 and 5; doors unresponsive. Two occupants inside..."
              required
            />
            <span className="form-hint">
              Be as specific as possible regarding floor, strange noises, cabin display readings, or passenger counts.
            </span>
          </div>

          {/* Action Row */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Submitting as: <strong style={{ color: 'var(--text-primary)' }}>{user?.fullName}</strong> ({user?.role})
            </div>
            <div style={{ display: 'flex', gap: '0.6rem' }}>
              <button
                type="button"
                onClick={() => navigate('/incidents')}
                className="btn btn-secondary"
                disabled={submitting}
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={submitting || loadingOptions}
                className={entrapmentReported ? 'btn btn-danger' : 'btn btn-primary'}
                style={{ minWidth: '150px' }}
              >
                {submitting
                  ? 'Dispatching Ticket...'
                  : entrapmentReported
                  ? '🚨 File Emergency Ticket'
                  : 'Submit Incident Ticket'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}
