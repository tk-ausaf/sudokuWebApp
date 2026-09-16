import { Link } from 'react-router-dom';

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString();
}

export default function AttemptListItem({ attempt }) {
  const isResumable = !attempt.completed && !attempt.failed && !attempt.abandoned;
  const statusLabel = attempt.completed
    ? 'Completed'
    : attempt.failed
      ? 'Failed'
      : attempt.abandoned
        ? 'Abandoned'
        : attempt.hasProgress
          ? 'In progress'
          : 'Not started';

  return (
    <li className="attempt-item">
      <div className="attempt-item__meta">
        <span className="attempt-item__title">{statusLabel}</span>
        <span className="attempt-item__subtitle">Started {formatDate(attempt.assignedAt)}</span>
      </div>
      {isResumable && (
        <Link className="btn btn--primary" to={`/resume/${attempt.attemptId}`}>
          Resume
        </Link>
      )}
      {attempt.completed && (
        <Link className="btn btn--ghost" to="/leaderboard">
          Leaderboard
        </Link>
      )}
    </li>
  );
}