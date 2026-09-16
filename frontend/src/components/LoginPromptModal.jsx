import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

/**
 * A login form shown as an overlay on top of whatever the user was doing (e.g. mid-puzzle),
 * so navigating to log in never loses unsaved page state. Calls `onClose` once login succeeds;
 * the caller is responsible for reacting to the resulting `isLoggedIn` change.
 */
export default function LoginPromptModal({ message, onClose }) {
  const { login } = useAuth();
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(name, password);
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Log in">
      <div className="modal-card card">
        <div className="page-header">
          <h2 style={{ margin: '0 0 4px', fontSize: '1.2rem' }}>Log in to continue</h2>
          {message && <p className="page-subtitle">{message}</p>}
        </div>
        <form onSubmit={handleSubmit}>
          {error && <p className="form-error">{error}</p>}
          <div className="form-field">
            <label htmlFor="modal-login-name">Username</label>
            <input id="modal-login-name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          </div>
          <div className="form-field">
            <label htmlFor="modal-login-password">Password</label>
            <input
              id="modal-login-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <div className="board-actions">
            <button className="btn btn--primary" type="submit" disabled={submitting}>
              {submitting ? 'Logging in...' : 'Log in'}
            </button>
            <button className="btn btn--ghost" type="button" onClick={onClose}>
              Cancel
            </button>
          </div>
        </form>
        <p className="page-subtitle" style={{ marginTop: 'var(--space-4)' }}>
          No account yet? <Link to="/register">Register</Link>
        </p>
      </div>
    </div>
  );
}