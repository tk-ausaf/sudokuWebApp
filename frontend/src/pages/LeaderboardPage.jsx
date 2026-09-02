import { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import LeaderboardTable from '../components/LeaderboardTable.jsx';

const PERIODS = [
  { value: 'daily', label: 'Today' },
  { value: 'weekly', label: 'This Week' },
  { value: 'monthly', label: 'This Month' },
  { value: 'yearly', label: 'This Year' },
];

export default function LeaderboardPage() {
  const [period, setPeriod] = useState('daily');
  const [entries, setEntries] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    setEntries(null);
    setError(null);
    api
      .getLeaderboard(period)
      .then(setEntries)
      .catch((err) => setError(err.message));
  }, [period]);

  return (
    <div className="page">
      <div className="page-header">
        <h1>Leaderboard</h1>
        <p className="page-subtitle">Ranked by puzzles solved - anyone can view this</p>
      </div>

      <div className="period-tabs">
        {PERIODS.map((p) => (
          <button
            key={p.value}
            className={`period-tab ${period === p.value ? 'period-tab--active' : ''}`}
            onClick={() => setPeriod(p.value)}
          >
            {p.label}
          </button>
        ))}
      </div>

      {error && <div className="page-state page-state--error">{error}</div>}
      {!error && !entries && <div className="page-state">Loading leaderboard...</div>}
      {entries && <LeaderboardTable entries={entries} />}
    </div>
  );
}