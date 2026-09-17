import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import SudokuBoard from './SudokuBoard.jsx';
import HeartMeter from './HeartMeter.jsx';
import LoginPromptModal from './LoginPromptModal.jsx';
import SaveNameModal from './SaveNameModal.jsx';
import DropdownMenu, { DropdownToggleItem } from './DropdownMenu.jsx';
import Icon from './Icon.jsx';

const AUTOSAVE_PREF_KEY = 'sudoku_autosave_enabled';
const AUTOSAVE_EVERY_N_MOVES = 5;

/**
 * Shared single-player gameplay UI for both a fresh puzzle (PlayPage) and a resumed one
 * (ResumePage): the board plus Submit/Undo/New puzzle/Save Progress controls, wrong-attempt
 * hearts, and the solved/failed end panels. `attempt.attemptId` may be null - see {@code
 * isPreview} below - for PlayPage's instantly-shown local puzzle preview, still waiting on a
 * real backend-tracked attempt for the same puzzle; Submit/Save/abandon are disabled until it
 * arrives. Render with a `key` from the parent that changes only when the underlying puzzle
 * itself changes (not merely when a preview attempt is swapped for its matching real one), so a
 * genuinely new attempt (new puzzle, resume, abandon+reload) gets fresh internal state while the
 * preview-to-real swap keeps whatever the player already typed.
 */
export default function SinglePlayerBoard({ attempt, title, subtitle, onReload }) {
  const { token, isLoggedIn } = useAuth();
  const [grid, setGrid] = useState(attempt.currentGrid);
  const [history, setHistory] = useState([]);
  const [completed, setCompleted] = useState(attempt.completed);
  const [failed, setFailed] = useState(attempt.failed);
  const [wrongAttempts, setWrongAttempts] = useState(attempt.wrongAttempts);
  const [statusMessage, setStatusMessage] = useState(null);
  const [flashBoard, setFlashBoard] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [saveState, setSaveState] = useState(null);
  const [savedName, setSavedName] = useState(attempt.name || null);
  const [showLoginModal, setShowLoginModal] = useState(false);
  const [showNameModal, setShowNameModal] = useState(false);
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
  // No attemptId yet means this is an instantly-shown local preview (see PlayPage) still waiting
  // on the real backend-tracked attempt for the same puzzle - editing is fine, but nothing that
  // needs a real attemptId (Submit/Save/abandon) can happen until it arrives.
  const isPreview = !attempt.attemptId;

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
      if (savedName) {
        doSave();
      } else {
        setShowNameModal(true);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  async function doSave(name) {
    setSaveState('saving');
    try {
      await api.autosave(token, attempt.attemptId, grid, name);
      if (name) setSavedName(name);
      setSaveState('saved');
      setTimeout(() => setSaveState(null), 2000);
    } catch {
      setSaveState('error');
      setTimeout(() => setSaveState(null), 3000);
    }
  }

  function handleSaveProgress() {
    if (isDone || isPreview) return;
    if (!isLoggedIn) {
      saveAfterLoginRef.current = true;
      setShowLoginModal(true);
      return;
    }
    if (!savedName) {
      setShowNameModal(true);
      return;
    }
    doSave();
  }

  function handleSaveName(name) {
    setShowNameModal(false);
    doSave(name);
  }

  function handleCellChange(next) {
    if (isDone) return;
    setHistory((prev) => [...prev, grid]);
    setGrid(next);
    moveCountRef.current += 1;
    if (!isPreview && isLoggedIn && autosaveEnabled && moveCountRef.current % AUTOSAVE_EVERY_N_MOVES === 0) {
      api.autosave(token, attempt.attemptId, next).catch(() => {});
    }
  }

  function handleUndo() {
    if (isDone || history.length === 0) return;
    const previous = history[history.length - 1];
    setHistory((prev) => prev.slice(0, -1));
    setGrid(previous);
  }

  async function handleSubmit() {
    if (isDone || submitting || isPreview) return;
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
    if (!isDone && !isPreview) {
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
            <HeartMeter total={attempt.maxWrongAttempts} used={wrongAttempts} />
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
          <button className="btn btn--primary" onClick={handleSubmit} disabled={submitting || isPreview}>
            {isPreview ? 'Connecting...' : submitting ? 'Checking...' : 'Submit'}
          </button>
          <button
            className="btn-icon"
            onClick={handleUndo}
            disabled={history.length === 0}
            title="Undo last move"
            aria-label="Undo last move"
          >
            <Icon name="undo" />
          </button>
          <button
            className="btn-icon"
            onClick={handleSaveProgress}
            disabled={isPreview}
            title="Save progress"
            aria-label="Save progress"
          >
            <Icon name="save" />
          </button>
          <button className="btn-icon" onClick={handleNewPuzzle} title="New puzzle" aria-label="New puzzle">
            <Icon name="refresh" />
          </button>
          {/* Rarely-changed preferences (not actions) live behind this gear, separate from the
              always-visible action icons above - future settings join it without growing the row. */}
          <DropdownMenu label="Game settings" trigger={<Icon name="settings" />}>
            <DropdownToggleItem checked={autosaveEnabled} onChange={setAutosaveEnabled}>
              Auto-save every 5 moves
            </DropdownToggleItem>
          </DropdownMenu>
        </div>
      )}

      {savedName && !isDone && <p className="page-subtitle">Saved as &ldquo;{savedName}&rdquo;</p>}

      {saveState === 'saving' && <p className="page-subtitle">Saving...</p>}
      {saveState === 'saved' && <p className="status-message status-message--success">Saved!</p>}
      {saveState === 'error' && <p className="status-message status-message--error">Couldn't save - try again.</p>}

      {!isDone && statusMessage && !completed && (
        <p className="status-message status-message--error">{statusMessage}</p>
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

      {showNameModal && (
        <SaveNameModal onSave={handleSaveName} onCancel={() => setShowNameModal(false)} />
      )}
    </div>
  );
}