import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

const OAUTH_ERROR_MESSAGES = {
  google_auth_failed: 'Google sign-in failed. Please try again.',
  google_account_conflict: 'An account with this email already exists - log in with your password instead.',
};

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(OAUTH_ERROR_MESSAGES[searchParams.get('error')] ?? null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(name, password);
      navigate('/');
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
          <h1>Log in</h1>
          <p className="page-subtitle">Track your history and appear on leaderboards</p>
        </div>
        <form onSubmit={handleSubmit}>
          {error && <p className="form-error">{error}</p>}
          <div className="form-field">
            <label htmlFor="login-name">Username</label>
            <input id="login-name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          </div>
          <div className="form-field">
            <label htmlFor="login-password">Password</label>
            <input
              id="login-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <button className="btn btn--primary" type="submit" disabled={submitting} style={{ width: '100%' }}>
            {submitting ? 'Logging in...' : 'Log in'}
          </button>
        </form>
        <a
          className="btn"
          href="/oauth2/authorization/google"
          style={{ width: '100%', display: 'block', textAlign: 'center', marginTop: 'var(--space-3)' }}
        >
          Sign in with Google
        </a>
        <p className="page-subtitle" style={{ marginTop: 'var(--space-4)' }}>
          No account yet? <Link to="/register">Register</Link>
        </p>
      </div>
    </div>
  );
}