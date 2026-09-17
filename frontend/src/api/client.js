import { markBackendUnavailable } from '../utils/backendAvailability.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Empty in local dev, so paths stay relative and keep hitting the Vite dev-server proxy to
// localhost:8080 (see vite.config.js); set at build time to the deployed backend's own origin,
// since the frontend and backend are separate services/domains in production.
const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

// Long enough not to misfire on an ordinary slow response, short enough not to make the user
// stare at a spinner for anywhere near the backend's full cold-start window.
const REQUEST_TIMEOUT_MS = 8000;

export function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** Thrown when a request never reached the backend at all (network failure or timeout) - as opposed to the backend responding with an error. */
export class BackendUnavailableError extends Error {
  constructor(message) {
    super(message);
    this.name = 'BackendUnavailableError';
  }
}

/** Runs `fetch`, converting a network-level failure or a timeout into a `BackendUnavailableError` and notifying the rest of the app. */
async function fetchWithAvailability(path, options) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    return await fetch(`${API_BASE}${path}`, { ...options, signal: controller.signal });
  } catch (err) {
    markBackendUnavailable();
    throw new BackendUnavailableError(
      err.name === 'AbortError' ? 'The server is taking a while to respond.' : 'Could not reach the server.',
    );
  } finally {
    clearTimeout(timeout);
  }
}

export async function requestJson(path, options = {}) {
  const response = await fetchWithAvailability(path, {
    credentials: 'include',
    ...options,
    headers: { ...JSON_HEADERS, ...(options.headers || {}) },
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body && body.message) {
        message = body.message;
      }
    } catch {
      // response body wasn't JSON - keep the default message
    }
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  getPuzzle: (token, preferredIndex) =>
    requestJson(`/sudoku/puzzle${preferredIndex != null ? `?index=${preferredIndex}` : ''}`, {
      headers: authHeaders(token),
    }),
  submit: (token, attemptId, grid) =>
    requestJson('/sudoku/submit', {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify({ attemptId, grid }),
    }),
  autosave: (token, attemptId, grid, name) =>
    requestJson(`/sudoku/attempts/${attemptId}/grid`, {
      method: 'PATCH',
      headers: authHeaders(token),
      body: JSON.stringify({ grid, ...(name ? { name } : {}) }),
    }),
  abandonAttempt: (token, attemptId) =>
    requestJson(`/sudoku/attempts/${attemptId}/abandon`, { method: 'POST', headers: authHeaders(token) }),
  getHistory: (token) => requestJson('/sudoku/attempts', { headers: authHeaders(token) }),
  resumeAttempt: (token, attemptId) =>
    requestJson(`/sudoku/attempts/${attemptId}`, { headers: authHeaders(token) }),
  getLeaderboard: (period) => requestJson(`/sudoku/leaderboard?period=${period}`),

  recordVisit: (token) => requestJson('/visitors/visit', { method: 'POST', headers: authHeaders(token) }),
  getTodayVisitorCount: () => requestJson('/visitors/today'),

  getMe: (token) => requestJson('/users/me', { headers: authHeaders(token) }),
  updateEmail: (token, email) =>
    requestJson('/users/email', { method: 'PATCH', headers: authHeaders(token), body: JSON.stringify({ email }) }),

  register: (name, password, email) =>
    requestJson('/users/addUser', { method: 'POST', body: JSON.stringify({ name, password, email: email || null }) }),

  requestPasswordReset: (email) =>
    requestJson('/users/forgot-password', { method: 'POST', body: JSON.stringify({ email }) }),
  resetPassword: (token, newPassword) =>
    requestJson('/users/reset-password', { method: 'POST', body: JSON.stringify({ token, newPassword }) }),

  // signIn returns a bare JWT string (or an empty body on bad credentials), not JSON.
  async login(name, password) {
    const response = await fetchWithAvailability('/users/signIn', {
      method: 'POST',
      credentials: 'include',
      headers: JSON_HEADERS,
      body: JSON.stringify({ name, password }),
    });
    if (!response.ok) {
      throw new Error('Login failed');
    }
    const text = await response.text();
    return text || null;
  },
};
