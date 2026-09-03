import { authHeaders, requestJson } from './client.js';

export const multiplayerApi = {
  createGame: (token, moveTimeLimitSeconds) =>
    requestJson('/multiplayer/games', {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify({ moveTimeLimitSeconds }),
    }),
  joinGame: (token, gameId) =>
    requestJson(`/multiplayer/games/${gameId}/join`, {
      method: 'POST',
      headers: authHeaders(token),
    }),
  getGame: (token, gameId) =>
    requestJson(`/multiplayer/games/${gameId}`, { headers: authHeaders(token) }),
};