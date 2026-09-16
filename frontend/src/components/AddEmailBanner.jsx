import { useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';

// Shown to logged-in accounts with no recovery email on file yet, as a single-line prompt that
// only expands into a form when clicked. Dismissing it only hides it for this page session - it
// reappears next time they log in, since adding an email stays optional but is what will let a
// future "forgot password" flow reach them.
export default function AddEmailBanner() {
  const { isLoggedIn, email, updateEmail } = useAuth();
  const [dismissed, setDismissed] = useState(false);
  const [expanded, setExpanded] = useState(false);
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
    <div className="email-nudge">
      <div className="email-nudge__inner">
        {expanded ? (
          <form onSubmit={handleSubmit} className="email-nudge__form">
            {error && (
              <p className="form-error" style={{ width: '100%', margin: 0 }}>
                {error}
              </p>
            )}
            <input
              type="email"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder="you@example.com"
              autoFocus
              required
            />
            <button className="btn btn--primary btn--sm" type="submit" disabled={submitting}>
              {submitting ? 'Saving...' : 'Save'}
            </button>
            <button className="btn btn--ghost btn--sm" type="button" onClick={() => setExpanded(false)}>
              Cancel
            </button>
          </form>
        ) : (
          <>
            <span>Add a recovery email so you can reset your password later.</span>
            <button className="btn btn--secondary btn--sm" type="button" onClick={() => setExpanded(true)}>
              Add email
            </button>
            <button
              className="btn-icon btn-icon--sm"
              type="button"
              onClick={() => setDismissed(true)}
              aria-label="Dismiss"
              title="Dismiss"
            >
              &times;
            </button>
          </>
        )}
      </div>
    </div>
  );
}
