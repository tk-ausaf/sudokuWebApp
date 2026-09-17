const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
const POLL_INTERVAL_MS = 5000;

let unavailable = false;
let pollTimer = null;
const listeners = new Set();

/** @return true if the backend was last observed to be unreachable. */
export function isBackendUnavailable() {
  return unavailable;
}

/** Subscribes to availability changes; call the returned function to unsubscribe. */
export function onBackendAvailabilityChange(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * Marks the backend as unreachable (e.g. a network-level fetch failure or timeout - see
 * `client.js`'s `BackendUnavailableError`) and starts polling its health endpoint until it
 * responds again. Safe to call repeatedly - a no-op while already marked unavailable.
 */
export function markBackendUnavailable() {
  if (unavailable) return;
  unavailable = true;
  notify();
  startPolling();
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = setInterval(async () => {
    try {
      const response = await fetch(`${API_BASE}/health`, { credentials: 'include' });
      if (response.ok) {
        markBackendAvailable();
      }
    } catch {
      // still down - keep polling
    }
  }, POLL_INTERVAL_MS);
}

function markBackendAvailable() {
  unavailable = false;
  clearInterval(pollTimer);
  pollTimer = null;
  notify();
}

function notify() {
  listeners.forEach((listener) => listener(unavailable));
}