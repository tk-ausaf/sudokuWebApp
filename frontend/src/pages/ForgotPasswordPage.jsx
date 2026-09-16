import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.js';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      // The backend always returns the same message whether or not this email is
      // registered, by design - it can't be used to discover who has an account.
      await api.requestPasswordReset(email);
      setSent(true);
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
          <h1>Reset your password</h1>
          <p className="page-subtitle">We'll email you a link if that address has an account</p>
        </div>
        {sent ? (
          <p className="status-message status-message--success">
            If an account with that email exists, a password reset link has been sent.
          </p>
        ) : (
          <form onSubmit={handleSubmit}>
            {error && <p className="form-error">{error}</p>}
            <div className="form-field">
              <label htmlFor="forgot-email">Email</label>
              <input
                id="forgot-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoFocus
              />
            </div>
            <button className="btn btn--primary" type="submit" disabled={submitting} style={{ width: '100%' }}>
              {submitting ? 'Sending...' : 'Send reset link'}
            </button>
          </form>
        )}
        <p className="page-subtitle" style={{ marginTop: 'var(--space-4)' }}>
          <Link to="/login">Back to log in</Link>
        </p>
      </div>
    </div>
  );
}