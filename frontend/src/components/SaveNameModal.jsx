import { useState } from 'react';

/** Overlay asking for a label to save the current game under, so it's identifiable later in History. */
export default function SaveNameModal({ onSave, onCancel }) {
  const [name, setName] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    onSave(trimmed);
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Name this game">
      <div className="modal-card card">
        <div className="page-header">
          <h2 style={{ margin: '0 0 4px', fontSize: '1.2rem' }}>Name this game</h2>
          <p className="page-subtitle">So you can tell it apart later in your history.</p>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="form-field">
            <label htmlFor="save-name-input">Name</label>
            <input
              id="save-name-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Sunday morning puzzle"
              autoFocus
              required
            />
          </div>
          <div className="board-actions">
            <button className="btn btn--primary" type="submit">
              Save
            </button>
            <button className="btn btn--ghost" type="button" onClick={onCancel}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}