import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { useAuth } from '../context/AuthContext.jsx';
import AttemptListItem from '../components/AttemptListItem.jsx';

export default function HistoryPage() {
  const { token } = useAuth();
  const [attempts, setAttempts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api
      .getHistory(token)
      .then(setAttempts)
      .catch((err) => setError(err.message));
  }, [token]);

  if (error) return <div className="page-state page-state--error">{error}</div>;
  if (!attempts) return <div className="page-state">Loading history...</div>;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Your Attempts</h1>
        <p className="page-subtitle">Resume any puzzle you haven't finished</p>
      </div>
      {attempts.length === 0 ? (
        <p className="page-subtitle">No attempts yet - go solve a puzzle!</p>
      ) : (
        <ul className="attempt-list">
          {attempts.map((attempt) => (
            <AttemptListItem key={attempt.attemptId} attempt={attempt} />
          ))}
        </ul>
      )}
    </div>
  );
}