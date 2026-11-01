/**
 * LiftCare API Client
 * Configurable backend communication with fallback and timeout handling.
 */

function resolveApiBaseUrl() {
  const envUrl = import.meta.env.VITE_API_BASE_URL;
  if (envUrl !== undefined && envUrl !== null && envUrl !== '') {
    return envUrl.endsWith('/') ? envUrl.slice(0, -1) : envUrl;
  }
  if (typeof window !== 'undefined') {
    // If running in development (e.g. port 5173), target the backend on the same host on port 8080
    if (window.location.port === '5173') {
      return `${window.location.protocol}//${window.location.hostname}:8080`;
    }
    // In production container (served by Nginx on port 80/any port with reverse proxy /api), use same-origin relative URL
    return '';
  }
  return 'http://localhost:8080';
}

const API_BASE_URL = resolveApiBaseUrl();

export async function evaluateSystemConnectivity() {
  const healthProbe = await checkBackendHealth(3500);
  const isBrowserNetworkConnected = typeof navigator !== 'undefined' ? navigator.onLine : true;

  let isInternetReachable = false;
  if (isBrowserNetworkConnected) {
    try {
      const internetController = new AbortController();
      const internetTimer = setTimeout(() => internetController.abort(), 1500);
      const probeRes = await fetch('https://connectivitycheck.gstatic.com/generate_204', {
        method: 'HEAD',
        mode: 'no-cors',
        signal: internetController.signal,
      }).catch(() => null);
      clearTimeout(internetTimer);
      if (probeRes) {
        isInternetReachable = true;
      }
    } catch {
      isInternetReachable = false;
    }
  }

  let state = 'UNKNOWN';
  let statusSummary = '';

  if (!healthProbe.reachable) {
    state = 'BACKEND_UNAVAILABLE';
    statusSummary = 'Local application server is unreachable. Verify that the local server process is running and the device is connected to the building LAN.';
  } else if (healthProbe.data?.database?.status !== 'CONNECTED') {
    state = 'DATABASE_UNAVAILABLE';
    statusSummary = 'Local backend is reachable, but local PostgreSQL database is disconnected. Database-dependent operations are halted.';
  } else if (!isInternetReachable) {
    state = 'LOCAL_LAN_AUTONOMY';
    statusSummary = 'Local building server & database fully operational over LAN. External internet is offline (offline-first mode active).';
  } else {
    state = 'LOCAL_ONLINE';
    statusSummary = 'Local building server, database, and building LAN operational. External internet access is active.';
  }

  return {
    state,
    statusSummary,
    health: healthProbe,
    isInternetReachable,
    isBrowserNetworkConnected,
    timestamp: new Date(),
  };
}

export async function checkBackendHealth(timeoutMs = 4000) {
  const startTime = performance.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(`${API_BASE_URL}/api/health`, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
      },
      signal: controller.signal,
    });

    clearTimeout(timer);
    const latency = Math.round(performance.now() - startTime);

    if (!response.ok) {
      let errorData = null;
      try {
        errorData = await response.json();
      } catch {
        // non-json response
      }
      return {
        reachable: false,
        status: response.status,
        statusText: response.statusText,
        error: errorData ? errorData.message || errorData.error : `HTTP ${response.status}`,
        latency,
        baseUrl: API_BASE_URL,
      };
    }

    const data = await response.json();
    return {
      reachable: true,
      status: response.status,
      latency,
      baseUrl: API_BASE_URL,
      data,
    };
  } catch (err) {
    clearTimeout(timer);
    const latency = Math.round(performance.now() - startTime);
    const isTimeout = err.name === 'AbortError';

    return {
      reachable: false,
      status: 0,
      latency,
      baseUrl: API_BASE_URL,
      error: isTimeout
        ? 'Connection timed out (>4000ms)'
        : (err.message || 'Unable to connect to LiftCare local server'),
      isTimeout,
    };
  }
}

/**
 * Authenticate with email & password against local building server.
 */
export async function loginUser(email, password) {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify({ email, password }),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.message || data.error || `Authentication failed (HTTP ${response.status})`);
  }
  return data;
}

/**
 * Register a new user on the local building server.
 */
export async function registerUser({ fullName, email, password, phoneNumber, requestedRole }) {
  const response = await fetch(`${API_BASE_URL}/api/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify({ fullName, email, password, phoneNumber, requestedRole }),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.values(data.validationErrors).join(', ')
      : (data.message || data.error || `Registration failed (HTTP ${response.status})`);
    throw new Error(errorMsg);
  }
  return data;
}

/**
 * Retrieve authenticated user profile with Bearer token.
 */
export async function fetchCurrentUser(token) {
  const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    throw new Error(data.message || data.error || `Failed to fetch profile (HTTP ${response.status})`);
  }
  return response.json();
}

/**
 * Log out (notifies server and returns acknowledgement).
 */
export async function logoutUser() {
  try {
    await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: 'POST',
      headers: { 'Accept': 'application/json' },
    });
  } catch (err) {
    console.warn('Logout notification error:', err);
  }
}

// =============================================================================
// Building Management APIs
// =============================================================================

export async function fetchBuildings(token, includeInactive = false) {
  const url = `${API_BASE_URL}/api/buildings${includeInactive ? '?includeInactive=true' : ''}`;
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch buildings (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchBuildingById(token, id) {
  const response = await fetch(`${API_BASE_URL}/api/buildings/${id}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch building #${id} (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function createBuilding(token, buildingData) {
  const response = await fetch(`${API_BASE_URL}/api/buildings`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(buildingData),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to create building (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function updateBuilding(token, id, buildingData) {
  const response = await fetch(`${API_BASE_URL}/api/buildings/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(buildingData),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to update building #${id} (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function deactivateBuilding(token, id) {
  const response = await fetch(`${API_BASE_URL}/api/buildings/${id}/deactivate`, {
    method: 'PATCH',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to deactivate building #${id} (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

// =============================================================================
// Incident Management & Local Triage APIs (Module 5)
// =============================================================================

export async function fetchIncidents(token, filters = {}) {
  const params = new URLSearchParams();
  if (filters.status) params.append('status', filters.status);
  if (filters.severity) params.append('severity', filters.severity);
  if (filters.buildingId) params.append('buildingId', filters.buildingId);
  if (filters.elevatorId) params.append('elevatorId', filters.elevatorId);
  if (filters.entrapmentOnly) params.append('entrapmentOnly', 'true');
  if (filters.assignedToId) params.append('assignedToId', filters.assignedToId);

  const queryString = params.toString() ? `?${params.toString()}` : '';
  const response = await fetch(`${API_BASE_URL}/api/incidents${queryString}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch incidents (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchMyIncidents(token) {
  const response = await fetch(`${API_BASE_URL}/api/incidents/my`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch your incidents (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchIncidentById(token, id) {
  const response = await fetch(`${API_BASE_URL}/api/incidents/${id}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch incident #${id} (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function createIncident(token, incidentData, idempotencyKey = null) {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': `Bearer ${token}`,
  };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }

  const response = await fetch(`${API_BASE_URL}/api/incidents`, {
    method: 'POST',
    headers,
    body: JSON.stringify(incidentData),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to report incident (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function acknowledgeIncident(token, id, remarks = '') {
  const url = `${API_BASE_URL}/api/incidents/${id}/acknowledge${remarks ? `?remarks=${encodeURIComponent(remarks)}` : ''}`;
  const response = await fetch(url, {
    method: 'PATCH',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to acknowledge incident #${id} (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function assignIncident(token, id, { technicianId, remarks }) {
  const response = await fetch(`${API_BASE_URL}/api/incidents/${id}/assign`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ technicianId, remarks }),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to assign technician (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function updateIncidentStatus(token, id, { status, remarks, elevatorStatus }) {
  const payload = { status, remarks };
  if (elevatorStatus) {
    payload.elevatorStatus = elevatorStatus;
  }
  const response = await fetch(`${API_BASE_URL}/api/incidents/${id}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to update status (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchIncidentHistory(token, id, params = {}) {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set('page', params.page);
  if (params.size !== undefined) query.set('size', params.size);
  if (params.sort) query.set('sort', params.sort);
  if (params.action) query.set('action', params.action);
  if (params.actorType) query.set('actorType', params.actorType);
  if (params.actorId) query.set('actorId', params.actorId);

  const qs = query.toString();
  const url = `${API_BASE_URL}/api/incidents/${id}/history${qs ? `?${qs}` : ''}`;
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch incident #${id} history (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function addIncidentNote(token, id, { notes, noteType }, idempotencyKey = null) {
  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    'Authorization': `Bearer ${token}`,
  };
  if (idempotencyKey) {
    headers['Idempotency-Key'] = idempotencyKey;
  }

  const response = await fetch(`${API_BASE_URL}/api/incidents/${id}/notes`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ notes, noteType }),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to record note (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchIssueCategories(token) {
  const response = await fetch(`${API_BASE_URL}/api/issue-categories`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ([]));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch issue categories (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchTechnicians(token) {
  const response = await fetch(`${API_BASE_URL}/api/users/technicians`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ([]));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch technicians (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchElevatorsByBuilding(token, buildingId, status = null) {
  const url = `${API_BASE_URL}/api/buildings/${buildingId}/elevators${status ? `?status=${status}` : ''}`;
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch elevators for building #${buildingId} (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchElevatorById(token, id) {
  const response = await fetch(`${API_BASE_URL}/api/elevators/${id}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch elevator #${id} (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function createElevator(token, buildingId, elevatorData) {
  const response = await fetch(`${API_BASE_URL}/api/buildings/${buildingId}/elevators`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(elevatorData),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to create elevator (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function updateElevator(token, id, elevatorData) {
  const response = await fetch(`${API_BASE_URL}/api/elevators/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(elevatorData),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const errorMsg = data.validationErrors
      ? Object.entries(data.validationErrors).map(([f, msg]) => `${f}: ${msg}`).join(', ')
      : (data.message || data.error || `Failed to update elevator #${id} (${response.status})`);
    const err = new Error(errorMsg);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function updateElevatorStatus(token, id, operationalStatus) {
  const response = await fetch(`${API_BASE_URL}/api/elevators/${id}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ operationalStatus }),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to update elevator status (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function deactivateElevator(token, id) {
  const response = await fetch(`${API_BASE_URL}/api/elevators/${id}/deactivate`, {
    method: 'PATCH',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to deactivate elevator #${id} (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchDashboardSummary(token, buildingId = null) {
  const url = `${API_BASE_URL}/api/dashboard/summary${buildingId ? `?buildingId=${buildingId}` : ''}`;
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch control-room summary (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchAllElevators(token, { buildingId = null, status = null } = {}) {
  const params = new URLSearchParams();
  if (buildingId) params.append('buildingId', buildingId);
  if (status) params.append('status', status);
  const queryString = params.toString() ? `?${params.toString()}` : '';

  const response = await fetch(`${API_BASE_URL}/api/elevators${queryString}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ([]));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch elevators (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export function getApiBaseUrl() {
  return API_BASE_URL;
}

// =============================================================================
// In-App Notifications & Outbox Queue Inspection APIs (Module 9)
// =============================================================================

export async function fetchMyNotifications(token) {
  const response = await fetch(`${API_BASE_URL}/api/notifications/my`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ([]));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch notifications (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function markNotificationAsRead(token, id) {
  const response = await fetch(`${API_BASE_URL}/api/notifications/${id}/read`, {
    method: 'PATCH',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to mark notification #${id} as read (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchOutboxSummary(token) {
  const response = await fetch(`${API_BASE_URL}/api/notifications/outbox`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch outbox summary (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchOutboxItems(token, { state = null, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams();
  if (state) params.append('state', state);
  params.append('page', page);
  params.append('size', size);

  const response = await fetch(`${API_BASE_URL}/api/notifications/outbox/items?${params.toString()}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch outbox items (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

// Module 10: SLA Policies & SLA Telemetry APIs

export async function fetchSlaPolicies(token, { buildingId = null, severity = null, activeOnly = false } = {}) {
  const params = new URLSearchParams();
  if (buildingId) params.append('buildingId', buildingId);
  if (severity) params.append('severity', severity);
  if (activeOnly) params.append('activeOnly', 'true');

  const url = `${API_BASE_URL}/api/sla-policies${params.toString() ? '?' + params.toString() : ''}`;
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ([]));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch SLA policies (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function fetchIncidentSla(token, incidentId) {
  const response = await fetch(`${API_BASE_URL}/api/incidents/${incidentId}/sla`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch incident SLA details (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function createSlaPolicy(token, policyData) {
  const response = await fetch(`${API_BASE_URL}/api/sla-policies`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(policyData),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to create SLA policy (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function updateSlaPolicy(token, policyId, policyData) {
  const response = await fetch(`${API_BASE_URL}/api/sla-policies/${policyId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(policyData),
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to update SLA policy (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function deactivateSlaPolicy(token, policyId) {
  const response = await fetch(`${API_BASE_URL}/api/sla-policies/${policyId}/deactivate`, {
    method: 'PATCH',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to deactivate SLA policy (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

// =========================================================================
// MODULE 12: Cloud Coordination & Multi-Building Synchronization APIs
// =========================================================================

export async function getLocalSyncStatus(token, buildingCode = '') {
  const url = buildingCode ? `${API_BASE_URL}/api/local-sync/status?buildingCode=${encodeURIComponent(buildingCode)}` : `${API_BASE_URL}/api/local-sync/status`;
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch local sync status (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function triggerLocalSync(token, buildingCode = '') {
  const url = buildingCode ? `${API_BASE_URL}/api/local-sync/trigger?buildingCode=${encodeURIComponent(buildingCode)}` : `${API_BASE_URL}/api/local-sync/trigger`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to trigger local sync (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function toggleSimulationOffline(token, offline) {
  const response = await fetch(`${API_BASE_URL}/api/local-sync/simulation/offline`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ offline }),
  });
  return response.json();
}

export async function getCentralDashboardSummary(token) {
  const response = await fetch(`${API_BASE_URL}/api/cloud/dashboard/summary`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch central dashboard (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function getCentralIncidents(token, { buildingCode = '', status = '', severity = '' } = {}) {
  const params = new URLSearchParams();
  if (buildingCode) params.append('buildingCode', buildingCode);
  if (status) params.append('status', status);
  if (severity) params.append('severity', severity);

  const query = params.toString() ? `?${params.toString()}` : '';
  const response = await fetch(`${API_BASE_URL}/api/cloud/incidents${query}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch central incidents (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function getCentralConflicts(token, status = '') {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  const response = await fetch(`${API_BASE_URL}/api/cloud/conflicts${query}`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch conflicts (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function resolveCentralConflict(token, conflictId, resolutionData) {
  const response = await fetch(`${API_BASE_URL}/api/cloud/conflicts/${conflictId}/resolve`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(resolutionData),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to resolve conflict (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function getRegisteredBuildings(token) {
  const response = await fetch(`${API_BASE_URL}/api/cloud/buildings`, {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to fetch registered buildings (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}

export async function registerBuilding(token, registrationData) {
  const response = await fetch(`${API_BASE_URL}/api/cloud/buildings/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(registrationData),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const err = new Error(data.message || data.error || `Failed to register building (${response.status})`);
    err.status = response.status;
    throw err;
  }
  return data;
}
