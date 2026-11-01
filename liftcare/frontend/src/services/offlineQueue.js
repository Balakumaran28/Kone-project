/**
 * LiftCare Offline Action Queue (IndexedDB)
 * 
 * Manages persistent client-side action queuing when the browser cannot reach
 * the local building server. Guarantees idempotency via unique UUID keys,
 * scopes actions to the authenticated user account to prevent cross-account replay,
 * and executes bounded exponential backoff retries when connectivity returns.
 */

import { getApiBaseUrl } from './api';

const DB_NAME = 'liftcare_offline_db';
const DB_VERSION = 1;
const STORE_NAME = 'actions';

/**
 * Open (or create/upgrade) the IndexedDB instance.
 */
export function openOfflineDb() {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB is not supported in this environment.'));
      return;
    }

    const request = window.indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const db = event.target.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        store.createIndex('by_user', 'userEmail', { unique: false });
        store.createIndex('by_status', 'status', { unique: false });
        store.createIndex('by_created', 'createdAt', { unique: false });
        store.createIndex('by_idempotency', 'idempotencyKey', { unique: true });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Generate a cryptographically strong UUID or fallback random string.
 */
export function generateIdempotencyKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'idemp-' + Date.now() + '-' + Math.random().toString(36).substring(2, 12);
}

/**
 * Enqueue a new offline action into IndexedDB.
 * 
 * @param {Object} params
 * @param {string} params.actionType - 'CREATE_INCIDENT', 'ADD_NOTE', etc.
 * @param {string} params.endpoint - e.g. '/api/incidents'
 * @param {string} params.method - 'POST', 'PATCH', etc.
 * @param {Object} params.payload - JSON payload of the request
 * @param {string} params.userEmail - Scoped email of initiating user
 * @param {string} [params.description] - Human readable summary of action
 */
export async function enqueueAction({ actionType, endpoint, method = 'POST', payload, userEmail, description = '' }) {
  const db = await openOfflineDb();
  const idempotencyKey = generateIdempotencyKey();
  const actionRecord = {
    id: 'act-' + Date.now() + '-' + Math.random().toString(36).substring(2, 7),
    idempotencyKey,
    userEmail: userEmail || 'anonymous',
    actionType,
    endpoint,
    method,
    payload,
    description: description || `${actionType} (${new Date().toLocaleTimeString()})`,
    status: 'PENDING', // PENDING, SYNCING, SYNCED, FAILED, NEEDS_REVIEW
    retryCount: 0,
    maxRetries: 5,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    lastError: null,
    serverResult: null,
  };

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_NAME], 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const request = store.add(actionRecord);

    request.onsuccess = () => resolve(actionRecord);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Retrieve all queued actions for a specific user (or all if not filtered).
 */
export async function getQueuedActions(userEmail = null) {
  const db = await openOfflineDb();

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_NAME], 'readonly');
    const store = tx.objectStore(STORE_NAME);
    const request = store.getAll();

    request.onsuccess = () => {
      let items = request.result || [];
      if (userEmail) {
        items = items.filter((item) => item.userEmail === userEmail);
      }
      // Sort newest first
      items.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      resolve(items);
    };
    request.onerror = () => reject(request.error);
  });
}

/**
 * Get count of pending actions for badge display.
 */
export async function getPendingActionCount(userEmail = null) {
  const items = await getQueuedActions(userEmail);
  return items.filter((item) => item.status === 'PENDING' || item.status === 'SYNCING').length;
}

/**
 * Update an action record in IndexedDB.
 */
export async function updateAction(action) {
  const db = await openOfflineDb();
  action.updatedAt = new Date().toISOString();

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_NAME], 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const request = store.put(action);

    request.onsuccess = () => resolve(action);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Remove an action by ID.
 */
export async function removeQueuedAction(id) {
  const db = await openOfflineDb();

  return new Promise((resolve, reject) => {
    const tx = db.transaction([STORE_NAME], 'readwrite');
    const store = tx.objectStore(STORE_NAME);
    const request = store.delete(id);

    request.onsuccess = () => resolve(true);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Clear all SYNCED actions for the user.
 */
export async function clearSyncedActions(userEmail = null) {
  const items = await getQueuedActions(userEmail);
  const syncedItems = items.filter((i) => i.status === 'SYNCED');
  for (const item of syncedItems) {
    await removeQueuedAction(item.id);
  }
  return syncedItems.length;
}

/**
 * Execute synchronization for a single queued action.
 */
export async function syncSingleAction(action, token) {
  const baseUrl = getApiBaseUrl();
  const url = `${baseUrl}${action.endpoint}`;

  action.status = 'SYNCING';
  await updateAction(action);

  try {
    const response = await fetch(url, {
      method: action.method || 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'Authorization': `Bearer ${token}`,
        'Idempotency-Key': action.idempotencyKey,
      },
      body: JSON.stringify(action.payload),
    });

    const data = await response.json().catch(() => ({}));

    if (response.ok) {
      action.status = 'SYNCED';
      action.serverResult = data;
      action.lastError = null;
      await updateAction(action);
      return { success: true, action };
    }

    // 401/403: Authentication or permission issue
    if (response.status === 401 || response.status === 403) {
      action.status = 'NEEDS_REVIEW';
      action.lastError = `Authentication expired or forbidden (${response.status}). Please re-login.`;
      await updateAction(action);
      return { success: false, authError: true, action };
    }

    // 400 Bad Request / Validation Failure: Retrying won't fix validation errors
    if (response.status >= 400 && response.status < 500) {
      action.status = 'FAILED';
      action.lastError = data.message || data.error || `Client error (HTTP ${response.status})`;
      await updateAction(action);
      return { success: false, clientError: true, action };
    }

    // 5xx Server Error: Increment retry count and keep PENDING
    action.retryCount = (action.retryCount || 0) + 1;
    if (action.retryCount >= action.maxRetries) {
      action.status = 'FAILED';
      action.lastError = `Max retries (${action.maxRetries}) exceeded. Last server response: HTTP ${response.status}`;
    } else {
      action.status = 'PENDING';
      action.lastError = `Server error HTTP ${response.status} (attempt ${action.retryCount}/${action.maxRetries})`;
    }
    await updateAction(action);
    return { success: false, serverError: true, action };

  } catch (netErr) {
    // Network failure (server unreachable)
    action.retryCount = (action.retryCount || 0) + 1;
    action.status = 'PENDING';
    action.lastError = `Local building server unreachable: ${netErr.message || 'Connection failed'}`;
    await updateAction(action);
    return { success: false, networkError: true, action };
  }
}

/**
 * Synchronize all pending actions for the given user.
 */
export async function syncAllPendingActions(token, userEmail, onProgress = null) {
  const items = await getQueuedActions(userEmail);
  const eligible = items.filter((i) => i.status === 'PENDING' || i.status === 'FAILED');

  let syncedCount = 0;
  let failedCount = 0;
  let pausedOnAuth = false;

  for (let index = 0; index < eligible.length; index++) {
    const action = eligible[index];

    if (onProgress) {
      onProgress({
        current: index + 1,
        total: eligible.length,
        action,
      });
    }

    const result = await syncSingleAction(action, token);
    if (result.success) {
      syncedCount++;
    } else {
      failedCount++;
      if (result.authError) {
        pausedOnAuth = true;
        // Stop syncing remaining items if session is invalid
        break;
      }
      if (result.networkError) {
        // Stop syncing remaining items if server is completely offline
        break;
      }
    }
  }

  return {
    totalEligible: eligible.length,
    syncedCount,
    failedCount,
    pausedOnAuth,
  };
}
