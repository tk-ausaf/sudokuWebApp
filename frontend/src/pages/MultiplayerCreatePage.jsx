import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { multiplayerApi } from '../api/multiplayerClient.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useMultiplayerSocket } from '../hooks/useMultiplayerSocket.js';

const DEFAULT_TIME_LIMIT = 60;

export default function MultiplayerCreatePage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [moveTimeLimitSeconds, setMoveTimeLimitSeconds] = useState(DEFAULT_TIME_LIMIT);
  const [game, setGame] = useState(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);
  const [copied, setCopied] = useState(false);

  const { lastEvent } = useMultiplayerSocket(game?.gameId, token);

  useEffect(() => {
    if (game && lastEvent?.eventType === 'PLAYER_JOINED') {
      navigate(`/multiplayer/game/${game.gameId}`);
    }
  }, [lastEvent, game, navigate]);

  async function handleCreate() {
    setCreating(true);
    setError(null);
    try {
      const created = await multiplayerApi.createGame(token, Number(moveTimeLimitSeconds));
      setGame(created);
    } catch (err) {
      setError(err.message);
    } finally {
      setCreating(false);
    }
  }

  const shareLink = game ? `${window.location.origin}/multiplayer/game/${game.gameId}` : null;

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(shareLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard API unavailable - user can still select/copy the link text manually
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>Multiplayer</h1>
        <p className="page-subtitle">Create a 1v1 game and send the link to a friend</p>
      </div>

      {!game && (
        <div className="card">
          <div className="form-field">
            <label htmlFor="move-time-limit">Time per move (seconds)</label>
            <input
              id="move-time-limit"
              type="number"
              min={5}
              max={600}
              value={moveTimeLimitSeconds}
              onChange={(event) => setMoveTimeLimitSeconds(event.target.value)}
            />
          </div>
          {error && <p className="form-error">{error}</p>}
          <div className="board-actions">
            <button className="btn btn--primary" disabled={creating} onClick={handleCreate}>
              {creating ? 'Creating...' : 'Create game'}
            </button>
          </div>
        </div>
      )}

      {game && (
        <div className="card">
          <p>Send this link to your opponent:</p>
          <div className="form-field">
            <input readOnly value={shareLink} onFocus={(event) => event.target.select()} />
          </div>
          <div className="board-actions">
            <button className="btn btn--secondary" onClick={handleCopy}>
              {copied ? 'Copied!' : 'Copy link'}
            </button>
          </div>
          <p className="page-subtitle">Waiting for your opponent to join...</p>
        </div>
      )}
    </div>
  );
}