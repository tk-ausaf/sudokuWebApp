import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import SudokuBoard from './SudokuBoard.jsx';
import LoginPromptModal from './LoginPromptModal.jsx';

const AUTOSAVE_PREF_KEY = 'sudoku_autosave_enabled';
const AUTOSAVE_EVERY_N_MOVES = 5;

/**
 * Shared single-player gameplay UI for both a fresh puzzle (PlayPage) and a resumed one
 * (ResumePage): the board plus Submit/New puzzle/Save Progress controls, wrong-attempt tracking,
 * and the solved/failed end panels. Render with `key={attempt.attemptId}` from the parent so a
 * newly loaded attempt (new puzzle, resume, abandon+reload) gets fresh internal state instead of
 * carrying over the previous attempt's grid/flash/wrong-attempt state.
 */
export default function SinglePlayerBoard({ attempt, title, subtitle, onReload }) {
  const { token, isLoggedIn } = useAuth();
  const [grid, setGrid] = useState(attempt.currentGrid);
  const [completed, setCompleted] = useState(attempt.completed);
  const [failed, setFailed] = useState(attempt.failed);
  const [wrongAttempts, setWrongAttempts] = useState(attempt.wrongAttempts);
  const [statusMessage, setStatusMessage] = useState(null);
  const [flashBoard, setFlashBoard] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [saveState, setSaveState] = useState(null);
  const [showLoginModal, setShowLoginModal] = useState(false);
  const [autosaveEnabled, setAutosaveEnabled] = useState(() => {
    try {
      return localStorage.getItem(AUTOSAVE_PREF_KEY) === 'true';
    } catch {
      return false;
    }
  });

  const saveAfterLoginRef = useRef(false);
  const moveCountRef = useRef(0);

  const isDone = completed || failed || attempt.abandoned;

  useEffect(() => {
    if (!flashBoard) return undefined;
    const timeout = setTimeout(() => setFlashBoard(null), 650);
    return () => clearTimeout(timeout);
  }, [flashBoard]);

  useEffect(() => {
    try {
      localStorage.setItem(AUTOSAVE_PREF_KEY, String(autosaveEnabled));
    } catch {
      // localStorage unavailable (e.g. private browsing) - the toggle just won't persist
    }
  }, [autosaveEnabled]);

  useEffect(() => {
    if (isLoggedIn && saveAfterLoginRef.current) {
      saveAfterLoginRef.current = false;
      doSave();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  async function doSave(gridToSave = grid) {
    setSaveState('saving');
    try {
      await api.autosave(token, attempt.attemptId, gridToSave);
      setSaveState('saved');
      setTimeout(() => setSaveState(null), 2000);
    } catch {
      setSaveState('error');
      setTimeout(() => setSaveState(null), 3000);
    }
  }

  function handleSaveProgress() {
    if (isDone) return;
    if (!isLoggedIn) {
      saveAfterLoginRef.current = true;
      setShowLoginModal(true);
      return;
    }
    doSave();
  }

  function handleCellChange(next) {
    if (isDone) return;
    setGrid(next);
    moveCountRef.current += 1;
    if (isLoggedIn && autosaveEnabled && moveCountRef.current % AUTOSAVE_EVERY_N_MOVES === 0) {
      api.autosave(token, attempt.attemptId, next).catch(() => {});
    }
  }

  async function handleSubmit() {
    if (isDone || submitting) return;
    setSubmitting(true);
    try {
      const result = await api.submit(token, attempt.attemptId, grid);
      setStatusMessage(result.message);
      setWrongAttempts(result.wrongAttempts);
      setFlashBoard({ type: result.correct ? 'correct' : 'wrong', nonce: Date.now() });
      if (result.correct) {
        setCompleted(true);
      } else if (result.failed) {
        setFailed(true);
      }
    } catch (err) {
      setStatusMessage(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleNewPuzzle() {
    if (!isDone) {
      const confirmed = window.confirm('Abandon this puzzle and start a new one?');
      if (!confirmed) return;
      try {
        await api.abandonAttempt(token, attempt.attemptId);
      } catch {
        // if the abandon call fails, still try to reload - worst case the same attempt comes back
      }
    }
    onReload();
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>{title}</h1>
        {subtitle && !isDone && <p className="page-subtitle">{subtitle}</p>}
        {!isDone && (
          <p className="page-subtitle">
            Wrong attempts: {wrongAttempts}/{attempt.maxWrongAttempts}
          </p>
        )}
      </div>

      <SudokuBoard
        clues={attempt.clueGrid}
        values={grid}
        onCellChange={handleCellChange}
        readOnly={isDone}
        flashBoard={flashBoard}
      />

      {!isDone && (
        <div className="board-actions">
          <button className="btn btn--primary" onClick={handleSubmit} disabled={submitting}>
            {submitting ? 'Checking...' : 'Submit'}
          </button>
          <button className="btn btn--secondary" onClick={handleSaveProgress}>
            {saveState === 'saving' ? 'Saving...' : saveState === 'saved' ? 'Saved!' : 'Save progress'}
          </button>
          <button className="btn btn--ghost" onClick={handleNewPuzzle}>
            New puzzle
          </button>
        </div>
      )}

      {saveState === 'error' && <p className="status-message status-message--error">Couldn't save - try again.</p>}

      {!isDone && statusMessage && !completed && (
        <p className="status-message status-message--error">{statusMessage}</p>
      )}

      {isLoggedIn && !isDone && (
        <label className="autosave-toggle" title="Automatically saves your progress every 5 moves.">
          <input
            type="checkbox"
            checked={autosaveEnabled}
            onChange={(e) => setAutosaveEnabled(e.target.checked)}
          />
          Auto-save every 5 moves
        </label>
      )}

      {completed && (
        <div className="success-panel">
          <div className="success-panel__icon" aria-hidden="true">
            &#10003;
          </div>
          <h2 className="success-panel__title">Congrats!</h2>
          <p className="success-panel__subtitle">You solved the puzzle.</p>
          <div className="board-actions">
            <button className="btn btn--primary" onClick={onReload}>
              Load new puzzle
            </button>
            <Link className="btn btn--ghost" to="/leaderboard">
              View leaderboard
            </Link>
          </div>
        </div>
      )}

      {failed && (
        <div className="success-panel success-panel--loss">
          <div className="success-panel__icon" aria-hidden="true">
            &#10007;
          </div>
          <h2 className="success-panel__title">Puzzle failed</h2>
          <p className="success-panel__subtitle">
            You used all {attempt.maxWrongAttempts} wrong attempts.
          </p>
          <div className="board-actions">
            <button className="btn btn--primary" onClick={onReload}>
              Try a new puzzle
            </button>
          </div>
        </div>
      )}

      {showLoginModal && (
        <LoginPromptModal
          message="Log in to save your progress - it'll pick up right where you left off."
          onClose={() => setShowLoginModal(false)}
        />
      )}
    </div>
  );
}