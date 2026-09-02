const JSON_HEADERS = { 'Content-Type': 'application/json' };

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function requestJson(path, options = {}) {
  const response = await fetch(path, {
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
  getPuzzle: (token) => requestJson('/sudoku/puzzle', { headers: authHeaders(token) }),
  submit: (token, attemptId, grid) =>
    requestJson('/sudoku/submit', {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify({ attemptId, grid }),
    }),
  autosave: (token, attemptId, grid) =>
    requestJson(`/sudoku/attempts/${attemptId}/grid`, {
      method: 'PATCH',
      headers: authHeaders(token),
      body: JSON.stringify({ grid }),
    }),
  getHistory: (token) => requestJson('/sudoku/attempts', { headers: authHeaders(token) }),
  resumeAttempt: (token, attemptId) =>
    requestJson(`/sudoku/attempts/${attemptId}`, { headers: authHeaders(token) }),
  getLeaderboard: (period) => requestJson(`/sudoku/leaderboard?period=${period}`),

  register: (name, password) =>
    requestJson('/users/addUser', { method: 'POST', body: JSON.stringify({ name, password }) }),

  // signIn returns a bare JWT string (or an empty body on bad credentials), not JSON.
  async login(name, password) {
    const response = await fetch('/users/signIn', {
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
