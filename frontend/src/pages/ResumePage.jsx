import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import SudokuBoard from '../components/SudokuBoard.jsx';
import { useAutosave } from '../hooks/useAutosave.js';

export default function ResumePage() {
  const { attemptId } = useParams();
  const { token } = useAuth();
  const [attempt, setAttempt] = useState(null);
  const [grid, setGrid] = useState(null);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    api
      .resumeAttempt(token, attemptId)
      .then((data) => {
        setAttempt(data);
        setGrid(data.currentGrid);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [attemptId, token]);

  useAutosave(
    grid,
    useCallback(
      (value) => {
        if (attempt && value && !attempt.completed && !status?.correct) {
          api.autosave(token, attemptId, value).catch(() => {});
        }
      },
      [attempt, attemptId, token, status],
    ),
  );

  async function handleSubmit() {
    try {
      const result = await api.submit(token, attemptId, grid);
      setStatus(result);
    } catch (err) {
      setStatus({ correct: false, message: err.message });
    }
  }

  if (loading) return <div className="page-state">Loading attempt...</div>;
  if (error) return <div className="page-state page-state--error">{error}</div>;
  if (!attempt) return null;

  const isDone = attempt.completed || Boolean(status?.correct);

  return (
    <div className="page">
      <div className="page-header">
        <h1>Resume Puzzle</h1>
      </div>

      <SudokuBoard clues={attempt.clueGrid} values={grid} onCellChange={setGrid} readOnly={isDone} />

      {!isDone && (
        <div className="board-actions">
          <button className="btn btn--primary" onClick={handleSubmit}>
            Submit
          </button>
        </div>
      )}

      {status && !status.correct && <p className="status-message status-message--error">{status.message}</p>}

      {isDone && (
        <div className="success-panel">
          <div className="success-panel__icon" aria-hidden="true">
            &#10003;
          </div>
          <h2 className="success-panel__title">Congrats!</h2>
          <p className="success-panel__subtitle">You solved the puzzle.</p>
          <div className="board-actions">
            <Link className="btn btn--primary" to="/">
              Load new puzzle
            </Link>
            <Link className="btn btn--ghost" to="/leaderboard">
              View leaderboard
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}