import { useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';

// Shown to logged-in accounts with no recovery email on file yet. Dismissing it only hides it
// for this page session - it reappears next time they log in, since adding an email stays
// optional but is what will let a future "forgot password" flow reach them.
export default function AddEmailBanner() {
  const { isLoggedIn, email, updateEmail } = useAuth();
  const [dismissed, setDismissed] = useState(false);
  const [value, setValue] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (!isLoggedIn || email !== null || dismissed) {
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await updateEmail(value);
      setDismissed(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card" style={{ margin: 'var(--space-3) auto', maxWidth: 480 }}>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'baseline', flexWrap: 'wrap' }}>
        <span>Add a recovery email so you can reset your password later:</span>
        {error && <p className="form-error" style={{ width: '100%', margin: 0 }}>{error}</p>}
        <input
          type="email"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="you@example.com"
          required
        />
        <button className="btn btn--primary" type="submit" disabled={submitting}>
          {submitting ? 'Saving...' : 'Save'}
        </button>
        <button className="btn btn--ghost" type="button" onClick={() => setDismissed(true)}>
          Not now
        </button>
      </form>
    </div>
  );
}