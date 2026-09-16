import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import SinglePlayerBoard from '../components/SinglePlayerBoard.jsx';

export default function ResumePage() {
  const { attemptId } = useParams();
  const { token } = useAuth();
  const navigate = useNavigate();
  const [attempt, setAttempt] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    api
      .resumeAttempt(token, attemptId)
      .then(setAttempt)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, [attemptId, token]);

  if (loading) return <div className="page-state">Loading attempt...</div>;
  if (error) return <div className="page-state page-state--error">{error}</div>;
  if (!attempt) return null;

  return (
    <SinglePlayerBoard
      key={attempt.attemptId}
      attempt={attempt}
      title="Resume Puzzle"
      onReload={() => navigate('/')}
    />
  );
}