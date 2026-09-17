import { useEffect, useState } from 'react';
import { isBackendUnavailable, onBackendAvailabilityChange } from '../utils/backendAvailability.js';

/**
 * Full-screen takeover shown whenever an API call couldn't reach the backend at all (as opposed
 * to the backend responding with an error) - most likely because it's cold-starting. Clears
 * itself automatically once `backendAvailability`'s background poll notices the backend
 * responding again; the user just retries whatever they were doing.
 */
export default function BackendWakingOverlay() {
  const [down, setDown] = useState(isBackendUnavailable());

  useEffect(() => onBackendAvailabilityChange(setDown), []);

  if (!down) return null;

  return (
    <div className="modal-overlay" role="alert">
      <div className="modal-card card" style={{ textAlign: 'center' }}>
        <div className="spinner" aria-hidden="true" />
        <h2 style={{ margin: 'var(--space-3) 0 4px' }}>Setting things up for you</h2>
        <p className="page-subtitle">
          Our server is just waking up - this can take up to about 2 minutes on the first request
          after a while idle. We'll carry on automatically once it's ready; no need to refresh.
        </p>
      </div>
    </div>
  );
}