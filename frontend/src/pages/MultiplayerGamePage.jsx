import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { multiplayerApi } from '../api/multiplayerClient.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useMultiplayerSocket } from '../hooks/useMultiplayerSocket.js';
import SudokuBoard from '../components/SudokuBoard.jsx';
import TurnTimer from '../components/TurnTimer.jsx';
import MultiplayerEndScreen from '../components/MultiplayerEndScreen.jsx';

const SIZE = 9;

export default function MultiplayerGamePage() {
  const { gameId } = useParams();
  const { token } = useAuth();
  const [game, setGame] = useState(null);
  const [grid, setGrid] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [joining, setJoining] = useState(false);

  const loadGame = useCallback(() => {
    setLoading(true);
    setError(null);
    return multiplayerApi
      .getGame(token, gameId)
      .then((data) => {
        setGame(data);
        setGrid(data.currentGrid);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [token, gameId]);

  useEffect(() => {
    loadGame();
  }, [loadGame]);

  const { lastEvent, sendMove } = useMultiplayerSocket(gameId, token);

  useEffect(() => {
    if (!lastEvent) return;

    if (lastEvent.eventType === 'PLAYER_JOINED') {
      loadGame();
      return;
    }

    setGame((prev) => {
      if (!prev) return prev;
      if (lastEvent.eventType === 'MOVE_ACCEPTED') {
        return {
          ...prev,
          status: 'IN_PROGRESS',
          currentTurn: lastEvent.nextTurn,
          turnDeadline: lastEvent.nextTurnDeadline,
        };
      }
      if (lastEvent.eventType === 'GAME_ENDED') {
        return {
          ...prev,
          status: 'COMPLETED',
          outcome: lastEvent.outcome,
          endReason: lastEvent.endReason,
          turnDeadline: null,
        };
      }
      return prev;
    });

    if (lastEvent.eventType === 'MOVE_ACCEPTED' && lastEvent.row != null && lastEvent.col != null) {
      setGrid((prev) => {
        if (!prev) return prev;
        const next = prev.split('');
        next[lastEvent.row * SIZE + lastEvent.col] = String(lastEvent.value);
        return next.join('');
      });
    }
  }, [lastEvent, loadGame]);

  async function handleJoin() {
    setJoining(true);
    setError(null);
    try {
      await multiplayerApi.joinGame(token, gameId);
      await loadGame();
    } catch (err) {
      setError(err.message);
    } finally {
      setJoining(false);
    }
  }

  function handleCellChange(next) {
    if (!game || !grid) return;
    const isMyTurn = game.status === 'IN_PROGRESS' && game.currentTurn === game.yourSlot;
    if (!isMyTurn) return;

    const index = findChangedIndex(grid, next);
    if (index === null) return;

    const digit = next[index];
    if (digit === '0') return;

    setGrid(next);

    const row = Math.floor(index / SIZE);
    const col = index % SIZE;
    sendMove(row, col, Number(digit));
  }

  if (loading) return <div className="page-state">Loading game...</div>;
  if (error) return <div className="page-state page-state--error">{error}</div>;
  if (!game) return null;

  if (game.status === 'WAITING_FOR_OPPONENT' && !game.yourSlot) {
    return (
      <div className="page">
        <div className="page-header">
          <h1>Join Multiplayer Game</h1>
          <p className="page-subtitle">You've been invited to a 1v1 sudoku race</p>
        </div>
        <div className="board-actions">
          <button className="btn btn--primary" disabled={joining} onClick={handleJoin}>
            {joining ? 'Joining...' : 'Join game'}
          </button>
        </div>
      </div>
    );
  }

  if (game.status === 'WAITING_FOR_OPPONENT' && game.yourSlot) {
    return <div className="page-state">Waiting for your opponent to join...</div>;
  }

  const isMyTurn = game.status === 'IN_PROGRESS' && game.currentTurn === game.yourSlot;
  const isDone = game.status === 'COMPLETED';

  return (
    <div className="page">
      <div className="page-header">
        <h1>Multiplayer</h1>
        {!isDone && (
          <p className="page-subtitle">
            {isMyTurn ? 'Your turn' : "Opponent's turn"}
            {game.turnDeadline && (
              <>
                {' — '}
                <TurnTimer deadline={game.turnDeadline} />
              </>
            )}
          </p>
        )}
      </div>

      <SudokuBoard clues={game.clueGrid} values={grid} onCellChange={handleCellChange} readOnly={!isMyTurn || isDone} />

      {isDone && (
        <MultiplayerEndScreen outcome={game.outcome} endReason={game.endReason} yourSlot={game.yourSlot} />
      )}
    </div>
  );
}

function findChangedIndex(prevGrid, nextGrid) {
  for (let i = 0; i < prevGrid.length; i++) {
    if (prevGrid[i] !== nextGrid[i]) return i;
  }
  return null;
}