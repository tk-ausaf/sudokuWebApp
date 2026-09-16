import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { multiplayerApi } from '../api/multiplayerClient.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useMultiplayerSocket } from '../hooks/useMultiplayerSocket.js';
import SudokuBoard from '../components/SudokuBoard.jsx';
import TurnTimer from '../components/TurnTimer.jsx';
import HeartMeter from '../components/HeartMeter.jsx';
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
  const [flash, setFlash] = useState(null);
  const [justBecameMyTurn, setJustBecameMyTurn] = useState(false);
  const wasMyTurnRef = useRef(false);

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
      if (lastEvent.eventType === 'MOVE_ACCEPTED' || lastEvent.eventType === 'WRONG_MOVE') {
        return {
          ...prev,
          status: 'IN_PROGRESS',
          currentTurn: lastEvent.nextTurn,
          turnDeadline: lastEvent.nextTurnDeadline,
          player1WrongAttempts: lastEvent.player1WrongAttempts,
          player2WrongAttempts: lastEvent.player2WrongAttempts,
        };
      }
      if (lastEvent.eventType === 'GAME_ENDED') {
        return {
          ...prev,
          status: 'COMPLETED',
          outcome: lastEvent.outcome,
          endReason: lastEvent.endReason,
          turnDeadline: null,
          player1WrongAttempts: lastEvent.player1WrongAttempts,
          player2WrongAttempts: lastEvent.player2WrongAttempts,
        };
      }
      return prev;
    });

    if (lastEvent.row != null && lastEvent.col != null) {
      // A correct digit fills the cell; a wrong one never did on the server, so clear it back in
      // case this is the mover's own client, which fills it optimistically before the response.
      const filledValue = lastEvent.eventType === 'MOVE_ACCEPTED' ? String(lastEvent.value) : '0';
      setGrid((prev) => {
        if (!prev) return prev;
        const next = prev.split('');
        next[lastEvent.row * SIZE + lastEvent.col] = filledValue;
        return next.join('');
      });
    }

    if (
      (lastEvent.eventType === 'MOVE_ACCEPTED' || lastEvent.eventType === 'WRONG_MOVE') &&
      lastEvent.row != null &&
      lastEvent.col != null
    ) {
      const index = lastEvent.row * SIZE + lastEvent.col;
      const type = lastEvent.eventType === 'MOVE_ACCEPTED' ? 'correct' : 'wrong';
      setFlash({ index, type, nonce: Date.now() });
    }
  }, [lastEvent, loadGame]);

  useEffect(() => {
    if (!flash) return undefined;
    const timeout = setTimeout(() => setFlash(null), 650);
    return () => clearTimeout(timeout);
  }, [flash]);

  const isMyTurnNow = Boolean(game && game.status === 'IN_PROGRESS' && game.currentTurn === game.yourSlot);

  useEffect(() => {
    if (isMyTurnNow && !wasMyTurnRef.current) {
      setJustBecameMyTurn(true);
      const timeout = setTimeout(() => setJustBecameMyTurn(false), 2500);
      wasMyTurnRef.current = isMyTurnNow;
      return () => clearTimeout(timeout);
    }
    wasMyTurnRef.current = isMyTurnNow;
    return undefined;
  }, [isMyTurnNow]);

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

  const isMyTurn = isMyTurnNow;
  const isDone = game.status === 'COMPLETED';
  const youArePlayer2 = game.yourSlot === 'PLAYER2';
  const yourName = youArePlayer2 ? game.player2Name : game.player1Name;
  const opponentName = youArePlayer2 ? game.player1Name : game.player2Name;
  const yourWrongAttempts = youArePlayer2 ? game.player2WrongAttempts : game.player1WrongAttempts;
  const opponentWrongAttempts = youArePlayer2 ? game.player1WrongAttempts : game.player2WrongAttempts;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Multiplayer</h1>
      </div>

      {!isDone && (
        <div className="mp-header">
          <div className={`mp-player ${isMyTurn ? 'mp-player--active' : ''}`}>
            <span className="mp-player__name">You{yourName ? ` (${yourName})` : ''}</span>
            <HeartMeter total={game.maxWrongAttempts} used={yourWrongAttempts} />
          </div>
          <div className={`mp-center ${justBecameMyTurn ? 'turn-banner--pulse' : ''}`}>
            <span className="mp-turn-label">{isMyTurn ? 'Your turn!' : "Opponent's turn"}</span>
            {game.turnDeadline && <TurnTimer deadline={game.turnDeadline} />}
          </div>
          <div className={`mp-player mp-player--right ${!isMyTurn ? 'mp-player--active' : ''}`}>
            <span className="mp-player__name">{opponentName || 'Opponent'}</span>
            <HeartMeter total={game.maxWrongAttempts} used={opponentWrongAttempts} />
          </div>
        </div>
      )}

      <SudokuBoard
        clues={game.clueGrid}
        values={grid}
        onCellChange={handleCellChange}
        readOnly={!isMyTurn || isDone}
        flash={flash}
      />

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
