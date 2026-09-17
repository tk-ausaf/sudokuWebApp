import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import SinglePlayerBoard from '../components/SinglePlayerBoard.jsx';

const DEFAULT_MAX_WRONG_ATTEMPTS = 5;

// Module-scoped so every PlayPage mount in this tab reuses the same fetch/parse instead of
// re-downloading puzzles.txt (served from the frontend's own static build, no backend involved).
let clueGridsPromise = null;
function loadClueGrids() {
  if (!clueGridsPromise) {
    clueGridsPromise = fetch('/puzzles.txt')
      .then((res) => res.text())
      .then((text) => text.split('\n').map((line) => line.trim()).filter(Boolean));
  }
  return clueGridsPromise;
}

function previewAttempt(clueGrid) {
  return {
    attemptId: null,
    clueGrid,
    currentGrid: clueGrid,
    completed: false,
    failed: false,
    abandoned: false,
    wrongAttempts: 0,
    maxWrongAttempts: DEFAULT_MAX_WRONG_ATTEMPTS,
    name: null,
  };
}

export default function PlayPage() {
  const { token } = useAuth();
  const [puzzle, setPuzzle] = useState(null);
  const [boardKey, setBoardKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const boardKeyRef = useRef(0);

  const loadPuzzle = useCallback(async () => {
    setLoading(true);
    setError(null);
    boardKeyRef.current += 1;
    setBoardKey(boardKeyRef.current);

    try {
      const clueGrids = await loadClueGrids();
      const index = Math.floor(Math.random() * clueGrids.length);
      setPuzzle(previewAttempt(clueGrids[index]));
      setLoading(false);

      // Fetch the real, backend-tracked attempt for this same puzzle in the background - the
      // instantly-shown preview above doesn't wait on this. If the caller already had a
      // different in-progress attempt, the backend resumes that instead (it always takes
      // priority over a brand-new index) - SinglePlayerBoard swaps to it once it arrives,
      // bumping boardKey only if it turns out to be a genuinely different puzzle.
      api
        .getPuzzle(token, index)
        .then((real) => {
          setPuzzle((prev) => {
            if (prev && prev.clueGrid !== real.clueGrid) {
              boardKeyRef.current += 1;
              setBoardKey(boardKeyRef.current);
            }
            return real;
          });
        })
        .catch(() => {
          // Backend not reachable/ready yet - the local preview stays playable; Submit/Save
          // stay disabled until this succeeds, and BackendWakingOverlay handles the rest.
        });
    } catch (err) {
      setError(err.message);
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    loadPuzzle();
  }, [loadPuzzle]);

  if (loading) return <div className="page-state">Loading a puzzle...</div>;
  if (error) return <div className="page-state page-state--error">{error}</div>;
  if (!puzzle) return null;

  return (
    <SinglePlayerBoard
      key={boardKey}
      attempt={puzzle}
      title="Sudoku"
      subtitle="A fresh puzzle, generated just for you"
      onReload={loadPuzzle}
    />
  );
}
