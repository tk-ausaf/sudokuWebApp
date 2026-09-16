import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setSubmitting(true);
    try {
      await api.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <div className="card" style={{ maxWidth: 360 }}>
        <div className="page-header">
          <h1>Choose a new password</h1>
        </div>
        {!token ? (
          <p className="form-error">This link is missing its reset token - use the link from your email.</p>
        ) : done ? (
          <p className="status-message status-message--success">
            Your password has been reset. <Link to="/login">Log in</Link>
          </p>
        ) : (
          <form onSubmit={handleSubmit}>
            {error && <p className="form-error">{error}</p>}
            <div className="form-field">
              <label htmlFor="reset-password">New password</label>
              <input
                id="reset-password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoFocus
              />
            </div>
            <div className="form-field">
              <label htmlFor="reset-confirm-password">Confirm new password</label>
              <input
                id="reset-confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
              />
            </div>
            <button className="btn btn--primary" type="submit" disabled={submitting} style={{ width: '100%' }}>
              {submitting ? 'Saving...' : 'Reset password'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}