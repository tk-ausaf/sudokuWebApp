import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import SudokuBoard from '../components/SudokuBoard.jsx';
import { useAutosave } from '../hooks/useAutosave.js';

export default function PlayPage() {
  const { token } = useAuth();
  const [puzzle, setPuzzle] = useState(null);
  const [grid, setGrid] = useState(null);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadPuzzle = useCallback(() => {
    setLoading(true);
    setError(null);
    setStatus(null);
    return api
      .getPuzzle(token)
      .then((data) => {
        setPuzzle(data);
        setGrid(data.currentGrid);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [token]);

  useEffect(() => {
    loadPuzzle();
  }, [loadPuzzle]);

  const isSolved = Boolean(status?.correct);

  useAutosave(
    grid,
    useCallback(
      (value) => {
        if (puzzle && value && !isSolved) {
          api.autosave(token, puzzle.attemptId, value).catch(() => {});
        }
      },
      [puzzle, token, isSolved],
    ),
  );

  async function handleSubmit() {
    if (!puzzle) return;
    try {
      const result = await api.submit(token, puzzle.attemptId, grid);
      setStatus(result);
    } catch (err) {
      setStatus({ correct: false, message: err.message });
    }
  }

  if (loading) return <div className="page-state">Loading a puzzle...</div>;
  if (error) return <div className="page-state page-state--error">{error}</div>;
  if (!puzzle) return null;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Sudoku</h1>
        <p className="page-subtitle">A fresh puzzle, generated just for you</p>
      </div>

      <SudokuBoard clues={puzzle.clueGrid} values={grid} onCellChange={setGrid} readOnly={isSolved} />

      {!isSolved && (
        <div className="board-actions">
          <button className="btn btn--primary" onClick={handleSubmit}>
            Submit
          </button>
          <Link className="btn btn--ghost" to="/leaderboard">
            View leaderboard
          </Link>
        </div>
      )}

      {status && !status.correct && (
        <p className="status-message status-message--error">{status.message}</p>
      )}

      {isSolved && (
        <div className="success-panel">
          <div className="success-panel__icon" aria-hidden="true">
            &#10003;
          </div>
          <h2 className="success-panel__title">Congrats!</h2>
          <p className="success-panel__subtitle">You solved the puzzle.</p>
          <div className="board-actions">
            <button className="btn btn--primary" onClick={loadPuzzle}>
              Load new puzzle
            </button>
            <Link className="btn btn--ghost" to="/leaderboard">
              View leaderboard
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
