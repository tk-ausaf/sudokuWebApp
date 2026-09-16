import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import SinglePlayerBoard from '../components/SinglePlayerBoard.jsx';

export default function PlayPage() {
  const { token } = useAuth();
  const [puzzle, setPuzzle] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadPuzzle = useCallback(() => {
    setLoading(true);
    setError(null);
    return api
      .getPuzzle(token)
      .then(setPuzzle)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [token]);

  useEffect(() => {
    loadPuzzle();
  }, [loadPuzzle]);

  if (loading) return <div className="page-state">Loading a puzzle...</div>;
  if (error) return <div className="page-state page-state--error">{error}</div>;
  if (!puzzle) return null;

  return (
    <SinglePlayerBoard
      key={puzzle.attemptId}
      attempt={puzzle}
      title="Sudoku"
      subtitle="A fresh puzzle, generated just for you"
      onReload={loadPuzzle}
    />
  );
}